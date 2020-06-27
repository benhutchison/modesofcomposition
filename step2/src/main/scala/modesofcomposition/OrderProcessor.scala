package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def resolveOrderMsg[F[_]: Async: Parallel: SkuLookup: CustomerLookup](msg: OrderMsg): F[CustomerOrder] = ???

}

