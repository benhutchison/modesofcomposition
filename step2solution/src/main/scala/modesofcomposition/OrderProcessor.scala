package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

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

}

