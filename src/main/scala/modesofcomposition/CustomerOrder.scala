package modesofcomposition

import cats.data.NonEmptyChain

case class CustomerOrder private (customerId: CustomerId, items: NonEmptyChain[SkuQuantity])

case class SkuQuantity(sku: Sku, quantity: PosInt)
