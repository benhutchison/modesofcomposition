package modesofcomposition

import io.chrisdavenport.cats.effect.time.JavaTime

import scala.collection.immutable.SortedSet

object OrderProcessor {

  /** Delegates to dispatchElseBackorder to determine whether the order can be dispatched, then publishes
   * the appropriate message. If   */
  def processAvailableOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
  (order: CustomerOrder): F[Unit] = {

    dispatchElseBackorder[F](order).>>= {
      case Right(dispatched) =>
        F.publish(Topic.Dispatch, dispatched.asJsonBytes)
      case Left((backorder, taken)) =>
        F.publish(Topic.Backorder, backorder.asJsonBytes)

    }
  }

  /** Key order business logic: try to take all ordered items from inventory. If all are in stock,
   * the order is dispatched. If any have insufficient stock, then the order wont proceed: return all items
   * to inventory and raise a backorder. */
  def dispatchElseBackorder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory](order: CustomerOrder):
  F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

    order.items.parTraverse(F.inventoryTake).>>=(takes =>
      insufficientsAndTaken(takes) match {
        case Some((insufficientStocks, taken)) =>
          taken.parTraverse_(F.inventoryPut) >>
          backorder(insufficientStocks, order).tupleRight(taken).map(_.asLeft)
        case None =>
          dispatch(order).map(_.asRight)
      })
  }

  /** Generate a backorder by calculating the shortfall in stock to satisfy order */
  def backorder[F[_]: Sync: Clock]
  (insufficientStocks: NonEmptyChain[InsufficientStock], order: CustomerOrder):
  F[Backorder] = {
    (
      insufficientStocks.traverse {
        case InsufficientStock(SkuQuantity(sku, required), available) =>
          PosInt.fromF[F](required - available).map(SkuQuantity(sku, _))
      },
      JavaTime[F].getInstant,
      ).mapN {
      case (requiredStock, time) => Backorder(requiredStock, order, time)
    }
  }

  /** generate a dispatch combining the order, a timestap and UUID */
  def dispatch[F[_]: Sync: Clock: UuidRef](order: CustomerOrder): F[Dispatched] = {
    (
      JavaTime[F].getInstant,
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

