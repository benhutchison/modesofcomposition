package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processCustomerOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish](
    order: CustomerOrder): F[Unit] = {

    val nonAvailableSkus: Chain[Sku] = ???

    NonEmptySet.fromSet(SortedSet.from(nonAvailableSkus.iterator)) match {
      case None =>
        processAvailableOrder[F](order)
      case Some(nonAvailableSet) =>
        ???
    }
  }

  //this is a no-op in step3
  def processAvailableOrder[F[_] : Functor: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
    (order: CustomerOrder): F[Unit] = Sync[F].unit
}

