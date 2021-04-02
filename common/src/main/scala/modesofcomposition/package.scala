import cats.effect.Ref

package object modesofcomposition extends RefinedSupport with ErrorValueSupport {

  type UuidRef[F[_]] = Ref[F, UuidSeed]
}
