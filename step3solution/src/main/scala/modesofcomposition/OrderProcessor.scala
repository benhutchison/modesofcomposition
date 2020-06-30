package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processCustomerOrder[F[_]: Functor: Sync: Parallel: Clock: UuidRef: Inventory: Publish](
    order: CustomerOrder): F[Unit] = {

    val nonAvailableSkus: Chain[Sku] =
      order.items.map(_.sku).filter(_.nonAvailableRegions.contains(order.customer.region))

    if (nonAvailableSkus.isEmpty)
      processAvailableOrder[F](order)
    else {
      JavaTime[F].getInstant.map(time =>
        //fromSetUnsafe is..er.. safe because if condition checked it is nonEmpty
        Unavailable(NonEmptySet.fromSetUnsafe(SortedSet.from(nonAvailableSkus.iterator)), order, time)
      ).>>=(response =>
          F.publish(Topic.Unavailable, response.asJson.toString.getBytes))
    }
  }

  //this is a no-op in step3
  def processAvailableOrder[F[_] : Functor: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
    (order: CustomerOrder): F[Unit] = F.unit
}

