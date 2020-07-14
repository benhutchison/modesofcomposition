import cats.effect.concurrent.Ref

package object modesofcomposition extends RefinedSupport with ErrorValueSupport with CirceSupport {

  type UuidRef[F[_]] = Ref[F, UuidSeed]
}
