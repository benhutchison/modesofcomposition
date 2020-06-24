package modesofcomposition


import scala.collection.mutable
import scala.concurrent.duration.TimeUnit

object TestSupport {

  def fromJsonBytes[T: Decoder](bytes: Array[Byte]) = {
    io.circe.parser.decode[T](new String(bytes))
  }

  def inventory[F[_]: Sync](initialStock: Map[Sku, NatInt]): TestInventory[F] =
    new TestInventory[F](initialStock)

  def clock[F[_]: Applicative](time: Long) = new Clock[F] {
    override def realTime(unit: TimeUnit): F[Long] = F.pure(time)

    override def monotonic(unit: TimeUnit): F[Long] = F.pure(time)
  }

}

case class TestSkuLookup[F[_]: Sync](skus: Map[String, Sku]) extends SkuLookup[F] {

  override def resolveSku(s: String): F[Either[String, Sku]] = F.pure(skus.get(s).toRight(s"Sku code not found: $s"))
}

case class TestCustomerLookup[F[_]](customerIds: Map[String, CustomerId]) extends CustomerLookup[F] {

  override def resolveCustomerId(customerId: String)(implicit F: Async[F]): F[Either[String, CustomerId]] =
    F.pure(customerIds.get(customerId).toRight(s"CustomerId code not found: $customerId"))
}



case class TestInventory[F[_]: Sync](var stock: Map[Sku, NatInt]) extends Inventory[F] {

  override def inventoryTake(skuQty: SkuQuantity): F[Either[InsufficientStock, SkuQuantity]] = F.delay(
    stock.get(skuQty.sku).toRight(InsufficientStock(skuQty, NatInt(0))).flatMap { stockQty =>
      NatInt.from(stockQty - skuQty.quantity) match {
        case Right(remaining) =>
          this.stock = stock.updated(skuQty.sku, remaining)
          skuQty.asRight
        case Left(insuffcientMsg) =>
          InsufficientStock(skuQty, stockQty).asLeft
      }
    })

  override def inventoryPut(skuQty: SkuQuantity): F[Unit] =
    F.delay(this.stock = stock.updatedWith(skuQty.sku)(current => current |+| (skuQty.quantity: NatInt).some))

}

class TestPublish[F[_]: Sync] extends Publish[F] {
  var messages: Map[String, Chain[Array[Byte]]] = Map.empty

  override def publish(topic: String, msg: Array[Byte]): F[Unit] =
    F.delay(this.messages = messages.updatedWith(topic)(_ <+> Chain(msg).some))
}