package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processMsgStream[F[_]: Concurrent: Parallel: Clock: UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
    msgs: fs2.Stream[F, Array[Byte]], maxParallel: Int = 20): fs2.Stream[F, Unit] =
    msgs.parEvalMapUnordered(maxParallel)(processMsg[F])


  def processMsg[F[_]: Async: Parallel :Clock :UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                                   msg: Array[Byte]): F[Unit] =
    decodeMsg[F](msg).>>=(processOrderMsg[F](_, msg)).handleErrorWith(e =>
      F.delay(System.err.println(s"Message decode fail: $e")) >> F.publish(Topic.Deadletter, msg))


  def processOrderMsg[F[_]: Async: Parallel : Clock : UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                     orderMsg: OrderMsg, msg: Array[Byte]): F[Unit] =
    resolveOrderMsg(orderMsg).>>=(processCustomerOrder[F](_)).
      handleErrorWith(e =>
        F.delay(System.err.println(s"Message processing failed on '${orderMsg}': $e")) >>
        F.publish(Topic.Deadletter, msg))


  def decodeMsg[F[_]: ApplicativeError[*[_], Throwable]](msg: Array[Byte]): F[OrderMsg] =
    errorValueFromEither[F](parser.decode[OrderMsg](new String(msg)))


  def resolveOrderMsg[F[_]: Async: Parallel: SkuLookup: CustomerLookup](msg: OrderMsg): F[CustomerOrder] = msg match {
    case OrderMsg(custIdStr, items) =>
      (
        F.resolveCustomerId(custIdStr).>>=(errorValueFromEither[F](_)),
        items.parTraverse { case (code, qty) =>
          (
            F.resolveSku(code).>>=(errorValueFromEither[F](_)),
            PosInt.fromF[F](qty),
          ).parMapN(SkuQuantity(_, _))
        },
      ).mapN(CustomerOrder(_, _))
    }

  def processCustomerOrder[F[_] : Functor : Async : Parallel : Clock : UuidRef: Inventory: Publish](
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

  def processAvailableOrder[F[_] : Functor : Async : Parallel : Clock : UuidRef: Inventory: Publish](order: CustomerOrder): F[Unit] = {
    order.items.parTraverse(F.inventoryTake).>>=(
      allTakes => dispatchElseBackorder[F](allTakes, order).>>= {
        case Right(dispatched) =>
          F.publish(Topic.Dispatch, dispatched.asJson.toString.getBytes)
        case Left((backorder, taken)) =>
          F.publish(Topic.Backorder, backorder.asJson.toString.getBytes) >>
            taken.parTraverse_(F.inventoryPut)
      })

  }

  def dispatchElseBackorder[F[_]: Functor: Async: Parallel: Clock: UuidRef](
                                                                             takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]], order: CustomerOrder):
  F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

    takes.toChain.separate match {
      case (insufficients, taken) =>
        NonEmptyChain.fromChain(insufficients) match {

          case Some(insufficientStocks) =>
            (
              insufficientStocks.traverse {
                case InsufficientStock(SkuQuantity(sku, required), available) =>
                  PosInt.fromF[F](required - available).map(SkuQuantity(sku, _))
              },
              JavaTime[F].getInstant,
              ).mapN {
              case (requiredStock, time) => (Backorder(requiredStock, order, time), taken).asLeft
            }

          case None =>
            (
              JavaTime[F].getInstant,
              UuidSeed.nextUuid[F]
              ).mapN {
              case (time, id) => Dispatched(order, time, id).asRight
            }
        }
    }
  }

}

