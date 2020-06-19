package modesofcomposition

import java.time.Instant
import java.util.UUID


case class Dispatched(order: CustomerOrder, timestamp: Instant, fulfillmentId: UUID)

case class Backorder(required: NonEmptyChain[SkuQuantity], order: CustomerOrder, timestamp: Instant)