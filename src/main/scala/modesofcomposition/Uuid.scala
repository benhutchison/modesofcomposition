package modesofcomposition

import java.util.UUID

final case class UuidSeed private (seeds: Array[Long]) {
  private def permute(l: Long) = l * 6364136223846793005L + 1442695040888963407L

  def next = UuidSeed(seeds.map(permute))
}

object UuidRng {

  def nextUuid(s: UuidSeed): (UuidSeed, UUID) = (s.next, new UUID(s.seeds(0) ^ s.seeds(1), s.seeds(2) ^ s.seeds(3)))

  def newSeed[F[_]: Sync]: F[UuidSeed] = F.delay(UuidSeed(Array.fill(4)(scala.util.Random.nextLong())))

  def nextUuidState[F[_]: Monad: UuidState]: F[UUID] = for {
    s <- F.get
    (s1, uuid) = nextUuid(s)
    _ <- F.set(s1)
  } yield (uuid)
}
