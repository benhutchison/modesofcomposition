package modesofcomposition

import java.util.Date

object OrderProcessor {

  def process[F[_]](order: CustomerOrderMsg): F[OrderResponse] = ???

}

sealed trait OrderResponse {
  def order: CustomerOrder
  def timestamp: Date
}

case class Dispatched(order: CustomerOrder, timestamp: Date, fulfillmentId: FulfillmentId)

case class Backorder(order: CustomerOrder, timestamp: Date)
