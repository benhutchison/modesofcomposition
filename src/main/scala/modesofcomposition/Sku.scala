package modesofcomposition

/** Sku (Shelf Keeping Unit) is a valid code identifying one purchasable product in the store */
case class Sku private (code: String)

/** Validates a sku code string is a valid Sku */
trait SkuLookup[F[_]] {

  def resolveSku(s: String): F[Either[String, Sku]]
}
