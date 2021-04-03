package modesofcomposition

import scala.collection.immutable.SortedSet


import java.util.UUID

object OrderProcessor {

  def processCustomerOrder[F[_]: Sync: Parallel: EventTime :UuidRef: Inventory: Publish](
    order: CustomerOrder): F[Unit] = {

    val nonAvailableSkus: Chain[Sku] =
      order.items.map(_.sku).filter(_.nonAvailableRegions.contains(order.customer.region))

    NonEmptySet.fromSet(SortedSet.from(nonAvailableSkus.iterator)) match {
      case None =>
        processAvailableOrder[F](order)
      case Some(nonAvailableSet) =>
        EventTime[F].currentInstant.map(time =>
          Unavailable(nonAvailableSet, order, time)
        ).>>=(response =>
          Publish[F].publish(Topic.Unavailable, response.asJson.toString.getBytes))
    }
  }

  //this is a no-op in step3
  def processAvailableOrder[F[_] : Sync: Parallel: EventTime :UuidRef: Inventory: Publish]
    (order: CustomerOrder): F[Unit] = Sync[F].unit
}

