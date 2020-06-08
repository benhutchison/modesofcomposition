package modesofcomposition

case class Sku private (code: String)

trait SkuLookup[F[_]] {

  def resolve(s: String): F[Either[String, Sku]]
}
