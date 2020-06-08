package modesofcomposition

case class OrderMsg(customerId: String, skuQuantities: NonEmptyChain[(String, Int)])