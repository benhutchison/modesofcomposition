package modesofcomposition

import java.time.Instant

/** The clock used for domain level events. We decouple this from `cats.effect.Clock` because it may not
 * in general be safe to freeze the runtime's Clock, but want to be free to freeze the EventTime to
 * constant value for the duration of processing. */
trait EventTime[F[_]] {

  def currentInstant: F[Instant]

}
object EventTime {

  def apply[F[_]](implicit et: EventTime[F]): EventTime[F] = et

  /** EventTime is based on `Clock` if not otherwise specified */
  implicit def default[F[_]](implicit clock: Clock[F]) = new EventTime[F] {
    override def currentInstant: F[Instant] = clock.realTimeInstant
  }
}
