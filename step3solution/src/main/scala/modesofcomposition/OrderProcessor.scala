package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processCustomerOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish](
    order: CustomerOrder): F[Unit] = {

    val nonAvailableSkus: Chain[Sku] =
      order.items.map(_.sku).filter(_.nonAvailableRegions.contains(order.customer.region))

    NonEmptySet.fromSet(SortedSet.from(nonAvailableSkus.iterator)) match {
      case None =>
        processAvailableOrder[F](order)
      case Some(nonAvailableSet) =>
        JavaTime[F].getInstant.map(time =>
          Unavailable(nonAvailableSet, order, time)
        ).>>=(response =>
          F.publish(Topic.Unavailable, response.asJson.toString.getBytes))
    }
  }

  //this is a no-op in step3
  def processAvailableOrder[F[_] : Sync: Parallel: Clock: UuidRef: Inventory: Publish]
    (order: CustomerOrder): F[Unit] = F.unit
}

