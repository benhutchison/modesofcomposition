package modesofcomposition

case class Sku private (code: String)

trait SkuLookup[F[_]] {

  def resolveSku(s: String): F[Either[String, Sku]]
}
