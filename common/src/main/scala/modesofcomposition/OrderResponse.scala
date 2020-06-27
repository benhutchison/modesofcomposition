package modesofcomposition

import java.util.UUID
import java.time.Instant

/** Domain event representing a valid order that can be sent for fulfilment. All items in the order are allocated from stock  */
case class Dispatched(order: CustomerOrder, timestamp: Instant, fulfillmentId: UUID)

/** Domain event representing an order not ready for fulfilment due to insufficient stock, to be backordered.
 * `required` holds quantities of all line items that need to be ordered from suppliers. */
case class Backorder(required: NonEmptyChain[SkuQuantity], order: CustomerOrder, timestamp: Instant)

/** Domain event representing an order not fulfilled because one or more requested Skus are not available
 * in the customers region. Refund required. */

case class Unavailable(unavailable: NonEmptySet[Sku], order: CustomerOrder, timestamp: Instant)