import cats.effect.concurrent.Ref

import scala.reflect.ClassTag

package object modesofcomposition {

  type RaiseErr[F[_]] = FunctorRaise[F, Throwable]
  type UuidRef[F[_]] = Ref[F, UuidSeed]

  type PosInt = Int Refined Positive
  type NatInt = Int Refined NonNegative

  object PosInt extends RefinedTypeOps[PosInt, Int] with RefineFOps[Int, Positive]
  object NatInt extends RefinedTypeOps[NatInt, Int] with RefineFOps[Int, Positive]

  trait RefineFOps[A, P] {

    def fromF[F[_]](a: A)(implicit F:  MonadError[F, Throwable], v: Validate[A, P]): F[Refined[A, P]] =
      F.fromEither(refineV[P](a).leftMap(e => new IllegalStateException(s"Refinement failed on $a: $e")))

  }

  class RefineFPartialApply[P, F[_]] {

    def apply[A](a: A)(implicit F:  MonadError[F, Throwable], v: Validate[A, P]): F[Refined[A, P]] =
      F.fromEither(refineV[P](a).leftMap(e => new IllegalStateException(s"Refinement fail: $e")))
  }

  def refineF[P, F[_]] = new RefineFPartialApply[P, F]


  def raiseTaggedError[F[_], E: ClassTag](e: E, stackTrace: Boolean = false)(implicit F: MonadError[F, Throwable]) =
    F.raiseError(new TaggedError(e, stackTrace))

  def taggedErrorFromEither[F[_]] = new TaggedErrorFromEitherPartiallyApplied[F]

  class TaggedErrorFromEitherPartiallyApplied[F[_]] {

    def apply[A, E: ClassTag](e: Either[E, A], stackTrace: Boolean = false)(implicit F: MonadError[F, Throwable]): F[A]
      = taggedErrorFromEitherF(e.pure[F])
  }

  def taggedErrorFromEitherF[F[_], A, E: ClassTag](e: F[Either[E, A]], stackTrace: Boolean = false)(
    implicit F: MonadError[F, Throwable]): F[A] =
    e.map(_.leftMap(new TaggedError(_, stackTrace))).flatMap(F.fromEither[A] _)

  case class TaggedError[E: ClassTag](e: E, stackTrace: Boolean = false) extends Throwable(e.toString, null, false, stackTrace)

}
