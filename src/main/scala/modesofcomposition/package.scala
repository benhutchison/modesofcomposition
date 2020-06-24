import cats.effect.concurrent.Ref

package object modesofcomposition extends RefinedSupport with ErrorValueSupport {

  type UuidRef[F[_]] = Ref[F, UuidSeed]
}
