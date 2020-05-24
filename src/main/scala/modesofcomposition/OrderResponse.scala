package modesofcomposition

import java.time.Instant
import java.util.UUID

sealed trait OrderResponse {
  def order: CustomerOrder
  def timestamp: Instant
}

case class Dispatched(order: CustomerOrder, timestamp: Instant, fulfillmentId: UUID) extends OrderResponse

case class Backorder(required: NonEmptyChain[SkuQuantity], order: CustomerOrder, timestamp: Instant) extends OrderResponse