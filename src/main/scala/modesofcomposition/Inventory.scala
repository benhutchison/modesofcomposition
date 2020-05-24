package modesofcomposition

trait Inventory[F[_]] {

  def take(skuQty: SkuQuantity): F[Either[InsufficientStock, Unit]]

  def put(skuQty: SkuQuantity): F[Unit]

}

case class InsufficientStock(requested: SkuQuantity, available: NatInt)
