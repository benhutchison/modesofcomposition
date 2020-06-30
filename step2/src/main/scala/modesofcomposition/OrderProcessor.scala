package modesofcomposition

object OrderProcessor {

  //resolveOrderMsg has been broken down into named parts to help understand the pieces of the computation
  def resolveOrderMsg[F[_]: Sync: Parallel: SkuLookup: CustomerLookup](msg: OrderMsg): F[CustomerOrder] =
    msg match { case OrderMsg(custIdStr, items) =>

      val resolveCustomer: F[Customer] = ???

      val resolveSkuQuantity: ((String, Int)) => F[SkuQuantity] = ???

      val resolveSkus: F[NonEmptyChain[SkuQuantity]] = ???

      //applicative composition
      (
        resolveCustomer,
        resolveSkus,
        ).parMapN(CustomerOrder(_, _))
    }
}

