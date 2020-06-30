package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

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

}

