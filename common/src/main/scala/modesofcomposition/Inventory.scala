package modesofcomposition

/** Interface to the stores product inventory */
trait Inventory[F[_]] {

  /** Take amount of product represented by skuQty from inventory, or if there is InsufficientStock provide details of it */
  def inventoryTake(skuQty: SkuQuantity): F[Either[InsufficientStock, SkuQuantity]]

  /** Add the amount of product represented by skuQty to inventory */
  def inventoryPut(skuQty: SkuQuantity): F[Unit]

}
object Inventory {

  def apply[F[_]](implicit i: Inventory[F]) = i
}
/** When an inventory take fails, details what was request and what is available. */
case class InsufficientStock(requested: SkuQuantity, available: NatInt) {
  require(available < requested.quantity, s"require available $available < requested.quantity ${requested.quantity}")
}
