package modesofcomposition

trait IsSku

trait Sku {

  def resolve[F[_]](s: String): F[Either[String, Sku]]
}
