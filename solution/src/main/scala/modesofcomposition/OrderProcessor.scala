package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processMsgStream[F[_]: Concurrent: Parallel: Clock: UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
    msgs: fs2.Stream[F, Array[Byte]], maxParallel: Int = 20): fs2.Stream[F, Unit] =
    msgs.parEvalMapUnordered(maxParallel)(processMsg[F])


  def processMsg[F[_]: Sync: Parallel :Clock :UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                                   msg: Array[Byte]): F[Unit] =
    decodeMsg[F](msg).>>=(processOrderMsg[F](_, msg)).handleErrorWith(e =>
      F.delay(System.err.println(s"Message decode fail: $e")) >> F.publish(Topic.Deadletter, msg))


  def processOrderMsg[F[_]: Sync: Parallel : Clock : UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                     orderMsg: OrderMsg, msg: Array[Byte]): F[Unit] =
    resolveOrderMsg(orderMsg).>>=(processCustomerOrder[F](_)).
      handleErrorWith(e =>
        F.delay(System.err.println(s"Message processing failed on '${orderMsg}': $e")) >>
        F.publish(Topic.Deadletter, msg))


  def decodeMsg[F[_]: ApplicativeError[*[_], Throwable]](msg: Array[Byte]): F[OrderMsg] =
    errorValueFromEither[F](parser.decode[OrderMsg](new String(msg)))


  //resolveOrderMsg has been broken down into named parts to help understand the pieces of the computation
  def resolveOrderMsg[F[_]: Sync: Parallel: SkuLookup: CustomerLookup](msg: OrderMsg): F[CustomerOrder] =
    msg match { case OrderMsg(custIdStr, items) =>

      val resolveCustomer: F[Customer] = F.resolveCustomerId(custIdStr).>>=(errorValueFromEither[F](_))

      val resolveSkuQuantity: ((String, Int)) => F[SkuQuantity] =
        { case (code, qty) =>
          (
            F.resolveSku(code).>>=(errorValueFromEither[F](_)),
            PosInt.fromF[F](qty),
          ).parMapN(SkuQuantity(_, _))
        }

      val resolveSkus: F[NonEmptyChain[SkuQuantity]] = items.parTraverse(resolveSkuQuantity)

      //applicative composition
      (
        resolveCustomer,
        resolveSkus,
      ).parMapN(CustomerOrder(_, _))
    }

  def processCustomerOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish](
    order: CustomerOrder): F[Unit] = {

    val nonAvailableSkus = order.items.map(_.sku).filter(_.nonAvailableRegions.contains(order.customer.region))
    if (nonAvailableSkus.isEmpty)
      processAvailableOrder[F](order)
    else {
      JavaTime[F].getInstant.map(time =>
        //fromSetUnsafe is..er.. safe because if condition checked it is nonEmpty
        Unavailable(NonEmptySet.fromSetUnsafe(SortedSet.from(nonAvailableSkus.iterator)), order, time)
      ).>>=(response =>
          F.publish(Topic.Unavailable, response.asJson.toString.getBytes))
    }
  }

  def processAvailableOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
  (order: CustomerOrder): F[Unit] = {

    dispatchElseBackorder[F](order).>>= {
      case Right(dispatched) =>
        F.publish(Topic.Dispatch, dispatched.asJson.toString.getBytes)
      case Left((backorder, taken)) =>
        F.publish(Topic.Backorder, backorder.asJson.toString.getBytes) >>
          taken.parTraverse_(F.inventoryPut)
    }
  }

  def dispatchElseBackorder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory](order: CustomerOrder):
  F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

    order.items.parTraverse(F.inventoryTake).>>=(takes =>
      insufficientsAndTaken(takes) match {
        case Some((insufficientStocks, taken)) =>
          backorder(insufficientStocks, order).tupleRight(taken).map(_.asLeft)
        case None =>
          dispatch(order).map(_.asRight)
      })
  }

  def backorder[F[_] : Sync : Parallel : Clock : UuidRef]
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

  def dispatch[F[_] : Sync : Parallel : Clock : UuidRef](order: CustomerOrder): F[Dispatched] = {
    (
      JavaTime[F].getInstant,
      UuidSeed.nextUuid[F]
      ).mapN {
      case (time, id) => Dispatched(order, time, id)
    }
  }

  def insufficientsAndTaken(takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]]):
  Option[(NonEmptyChain[InsufficientStock], Chain[SkuQuantity])] = {

    val (allInsufficients, taken) = takes.toChain.separate
    NonEmptyChain.fromChain(allInsufficients).tupleRight(taken)
  }

}

