package modesofcomposition

import scala.util.control.NoStackTrace
import com.vladkopanev.cats.saga.Saga
import com.vladkopanev.cats.saga.Saga._

object SagaExtensions {

  case class LeftError[E](e: E) extends Throwable with NoStackTrace

  /** Compensate if the action succeeds but yields a Left. Don't compensate if the action fails. */
  def compensateE[F[_]: MonadError[*[_], Throwable], A, E](action: F[Either[E, A]])(compensation: E => F[Unit]) = {
    Saga.noCompensate(action).flatMap[A] {
      case Right(a) => Saga.succeed(a)
      case Left(e) => LeftError(e).raiseError[F, A].compensateIfFail[Throwable](_ => compensation(e))
    }
  }

}