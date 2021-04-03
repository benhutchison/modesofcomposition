package modesofcomposition

import java.util.UUID

/** A 256bit immutable pseudo-random generator seed.
 *
 * A 256bit seed is the smallest seed required (on average) to generate a full random distribution through the 128bit
 * space of UUIDs.  */
final case class UuidSeed(seeds: Array[Long]) {
  require(seeds.length == 4)

  //https://en.wikipedia.org/wiki/Linear_congruential_generator
  private def permute(l: Long) = l * 6364136223846793005L + 1442695040888963407L

  def next = UuidSeed(seeds.map(permute))

  def uuid = new UUID(seeds(0) ^ seeds(1), seeds(2) ^ seeds(3))
}

object UuidSeed {

  def newSeed[F[_]: Sync]: F[UuidSeed] = Sync[F].delay(UuidSeed(Array.fill(4)(scala.util.Random.nextLong())))

  def nextUuid[F[_]: Functor: UuidRef]: F[UUID] = implicitly[Ref[F, UuidSeed]].getAndUpdate(_.next).map(_.uuid)
}
