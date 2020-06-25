package modesofcomposition

/** Key domain model that defines a validated customer order */
case class CustomerOrder private[modesofcomposition] (customerId: CustomerId, items: NonEmptyChain[SkuQuantity])

/** A validated line-item in an order, being a valid Sku and the number of that item ordered. */
case class SkuQuantity(sku: Sku, quantity: PosInt)
