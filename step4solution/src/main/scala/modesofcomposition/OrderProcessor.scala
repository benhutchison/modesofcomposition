package modesofcomposition



import scala.collection.immutable.SortedSet

object OrderProcessor {

  /** Delegates to dispatchElseBackorder to determine whether the order can be dispatched, then publishes
   * the appropriate message. If   */
  def processAvailableOrder[F[_]: Sync: Parallel: EventTime :UuidRef: Inventory: Publish]
  (order: CustomerOrder): F[Unit] = {

    dispatchElseBackorder[F](order).>>= {
      case Right(dispatched) =>
        Publish[F].publish(Topic.Dispatch, dispatched.asJson.toString.getBytes)
      case Left((backorder, taken)) =>
        Publish[F].publish(Topic.Backorder, backorder.asJson.toString.getBytes)

    }
  }

  /** Key order business logic: try to take all ordered items from inventory. If all are in stock,
   * the order is dispatched. If any have insufficient stock, then the order wont proceed: return all items
   * to inventory and raise a backorder. */
  def dispatchElseBackorder[F[_]: Sync: Parallel: EventTime :UuidRef: Inventory](order: CustomerOrder):
  F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

    order.items.parTraverse(Inventory[F].inventoryTake).>>=(takes =>
      insufficientsAndTaken(takes) match {
        case Some((insufficientStocks, taken)) =>
          taken.parTraverse_(Inventory[F].inventoryPut) >>
          backorder(insufficientStocks, order).tupleRight(taken).map(_.asLeft)
        case None =>
          dispatch(order).map(_.asRight)
      })
  }

  /** Generate a backorder by calculating the shortfall in stock to satisfy order */
  def backorder[F[_]: Sync: EventTime]
  (insufficientStocks: NonEmptyChain[InsufficientStock], order: CustomerOrder):
  F[Backorder] = {
    (
      insufficientStocks.traverse {
        case InsufficientStock(SkuQuantity(sku, required), available) =>
          PosInt.fromF[F](required - available).map(SkuQuantity(sku, _))
      },
      EventTime[F].currentInstant,
      ).mapN {
      case (requiredStock, time) => Backorder(requiredStock, order, time)
    }
  }

  /** generate a dispatch combining the order, a timestap and UUID */
  def dispatch[F[_]: Sync: EventTime :UuidRef](order: CustomerOrder): F[Dispatched] = {
    (
      EventTime[F].currentInstant,
      UuidSeed.nextUuid[F]
      ).mapN {
      case (time, id) => Dispatched(order, time, id)
    }
  }

  /** Transform a collection of inventory.take outcomes into details of a possible shortfall:
   * which items had insufficient stock, and which items were actually taken (since they need to be returned) */
  def insufficientsAndTaken(takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]]):
  Option[(NonEmptyChain[InsufficientStock], Chain[SkuQuantity])] = {

    val (allInsufficients, taken) = takes.toChain.separate
    NonEmptyChain.fromChain(allInsufficients).tupleRight(taken)
  }

}

