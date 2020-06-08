package modesofcomposition

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object MessageResolver {

  def resolve[F[_]: Async: Parallel](msg: String, skuLookup: SkuLookup[F], customerLookup: CustomerLookup[F]): F[CustomerOrder] = {

    taggedErrorFromEither[F](parseMsg(msg)).>>= {
    case OrderMsg(custIdStr, items) =>
      (
        taggedErrorFromEitherF(customerLookup.resolve(custIdStr)),
        items.parTraverse { case (code, qty) =>
          (
            taggedErrorFromEitherF(skuLookup.resolve(code)),
            refineF[Positive, F](qty),
          ).parMapN(SkuQuantity(_, _))
        },
      ).mapN(CustomerOrder(_, _))
    }
  }

  def parseMsg(msg: String): Either[Error, OrderMsg] = io.circe.parser.decode[OrderMsg](msg)



  def toDLQ[F[_]](msg: Json): F[Unit] = ???

}

