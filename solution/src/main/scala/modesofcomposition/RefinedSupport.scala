package modesofcomposition

trait RefinedSupport {
  //1, 2, 3 etc
  type PosInt = Int Refined Positive
  object PosInt extends RefinedTypeOps[PosInt, Int] with RefineFOps[Int, Positive]

  //0, 1, 2, 3 etc
  type NatInt = Int Refined NonNegative
  object NatInt extends RefinedTypeOps[NatInt, Int] with RefineFOps[Int, Positive]

  trait RefineFOps[A, P] {

    def fromF[F[_]](a: A)(implicit F:  MonadError[F, Throwable], v: Validate[A, P]): F[Refined[A, P]] =
      F.fromEither(refineV[P](a).leftMap(e => new IllegalStateException(s"Refinement failed on $a: $e")))

  }

}
