package modesofcomposition

trait Inventory[F[_]] {

  def inventoryTake(skuQty: SkuQuantity): F[Either[InsufficientStock, SkuQuantity]]

  def inventoryPut(skuQty: SkuQuantity): F[Unit]

}

case class InsufficientStock(requested: SkuQuantity, available: NatInt)
