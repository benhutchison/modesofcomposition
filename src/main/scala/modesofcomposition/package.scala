package object modesofcomposition {

  type RaiseErr[F[_]] = FunctorRaise[F, Throwable]
  type UuidState[F[_]] = MonadState[F, UuidSeed]

  type Sku = String Refined IsSku
  type PosInt = Int Refined Positive
  type NatInt = Int Refined NonNegative

}
