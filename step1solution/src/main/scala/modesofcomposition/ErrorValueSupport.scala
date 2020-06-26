package modesofcomposition

import scala.reflect.ClassTag
import scala.util.control.NonFatal

/** ErrorValues are a way of embedding errors that are ordinary non-Throwable values into the Throwable
 * inheritance hierarchy, by wrapping the value `e` in a wrapper class.
 *
 * For convenience and safety, the error value has a `ClassTag` stored, which enables pattern matching on different error
 * values in handler-code.
 *
 * Optionally, a stack trace can be recorded at the time the error value is created (default=false).
 * */
case class ErrorValue[E: ClassTag](e: E, stackTrace: Boolean = false) extends Throwable(
  e.toString, null, false, stackTrace)

trait ErrorValueSupport {

  /** Raise the value `e` as an error. Requires a ApplicativeError error effect in F. */
  def raiseErrorValue[F[_], E: ClassTag](e: E, stackTrace: Boolean = false)(implicit F: ApplicativeError[F, Throwable]) =
    F.raiseError(new ErrorValue(e, stackTrace))

  /**Convert an Either[E, A] into an F[A] by raising the E if required. Requires a MonadError error effect in F. */
  def errorValueFromEither[F[_]] = new ErrorValueFromEitherPartiallyApplied[F]

  class ErrorValueFromEitherPartiallyApplied[F[_]] {

    def apply[A, E: ClassTag](e: => Either[E, A], stackTrace: Boolean = false)(
      implicit F: ApplicativeError[F, Throwable]): F[A] = try {
      e match {
        case Right(a) => F.pure(a)
        case Left(t: Throwable) => F.raiseError[A](t)
        case Left(value: E) => F.raiseError[A](new ErrorValue(value, stackTrace))
      }
    } catch {
      case NonFatal(t) => F.raiseError(t)
    }
  }

}
