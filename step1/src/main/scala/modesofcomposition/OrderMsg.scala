package modesofcomposition

/** An incoming order request that has been parsed but not yet validated */
case class OrderMsg(customerId: String, skuQuantities: NonEmptyChain[(String, Int)])