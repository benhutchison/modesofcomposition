package modesofcomposition

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object MessageProcessor {

  def process[F[_]: Async: Parallel :Clock :UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                                   msg: Array[Byte]): F[Unit] =
    decode[F](msg).>>=(processOrderMsg[F](_, msg)).handleErrorWith(e =>
      F.delay(System.err.println(s"Message decode fail: $e")) >> F.publish("DEADLETTER", msg))


  def processOrderMsg[F[_]: Async: Parallel : Clock : UuidRef: SkuLookup: CustomerLookup: Inventory: Publish](
                                                     orderMsg: OrderMsg, msg: Array[Byte]): F[Unit] =
    resolve(orderMsg).>>=(OrderProcessor.process[F](_)).
      handleErrorWith(e =>
        F.delay(System.err.println(s"Message processing failed on '${orderMsg}': $e")) >>
        F.publish("DEADLETTER", msg))


  def decode[F[_]: Sync](msg: Array[Byte]): F[OrderMsg] =
    F.delay(new String(msg)).>>=((s) => taggedErrorFromEither[F](parser.decode[OrderMsg](s)))


  def resolve[F[_]: Async: Parallel: SkuLookup: CustomerLookup](msg: OrderMsg): F[CustomerOrder] = msg match {
    case OrderMsg(custIdStr, items) =>
      (
        taggedErrorFromEitherF(F.resolveCustomerId(custIdStr)),
        items.parTraverse { case (code, qty) =>
          (
            taggedErrorFromEitherF(F.resolveSku(code)),
            PosInt.fromF[F](qty),
          ).parMapN(SkuQuantity(_, _))
        },
      ).mapN(CustomerOrder(_, _))
    }

}

