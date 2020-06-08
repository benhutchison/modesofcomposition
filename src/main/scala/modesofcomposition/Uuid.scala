package modesofcomposition

import java.util.UUID

final case class UuidSeed(seeds: Array[Long]) {
  private def permute(l: Long) = l * 6364136223846793005L + 1442695040888963407L

  def next = UuidSeed(seeds.map(permute))

  def uuid = new UUID(seeds(0) ^ seeds(1), seeds(2) ^ seeds(3))
}

object UuidSeed {

  def newSeed[F[_]: Sync]: F[UuidSeed] = F.delay(UuidSeed(Array.fill(4)(scala.util.Random.nextLong())))

  def nextUuid[F[_]: Functor: UuidRef]: F[UUID] = F.getAndUpdate(_.next).map(_.uuid)
}
