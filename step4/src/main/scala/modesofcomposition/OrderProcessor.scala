package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processAvailableOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
  (order: CustomerOrder): F[Unit] = {
    ???
  }

  def dispatchElseBackorder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory](order: CustomerOrder):
  F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {
    ???
  }

  def backorder[F[_] : Sync : Parallel : Clock : UuidRef](insufficientStocks: NonEmptyChain[InsufficientStock],
  order: CustomerOrder): F[Backorder] = {
    ???
  }

  def dispatch[F[_] : Sync : Parallel : Clock : UuidRef](order: CustomerOrder): F[Dispatched] = {
    ???
  }

  def insufficientsAndTaken(takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]]):
  Option[(NonEmptyChain[InsufficientStock], Chain[SkuQuantity])] = {
    ???
  }


}

