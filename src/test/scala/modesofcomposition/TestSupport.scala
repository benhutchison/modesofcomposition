package modesofcomposition

import io.circe.Decoder

import scala.collection.mutable
import scala.concurrent.duration.TimeUnit

object TestSupport {

  def fromJsonBytes[T: Decoder](bytes: Array[Byte]) = io.circe.parser.decode[T](new String(bytes)).toOption.get

  def inventory[F[_]: Applicative](initialStock: Map[Sku, NatInt]): TestInventory[F] =
    new TestInventory[F](initialStock)

  def clock[F[_]: Applicative](time: Long) = new Clock[F] {
    override def realTime(unit: TimeUnit): F[Long] = F.pure(time)

    override def monotonic(unit: TimeUnit): F[Long] = F.pure(time)
  }

}

case class TestInventory[F[_]: Applicative](var stock: Map[Sku, NatInt]) extends Inventory[F] {

  override def take(skuQty: SkuQuantity): F[Either[InsufficientStock, Unit]] = F.pure(
    stock.get(skuQty.sku).toRight(InsufficientStock(skuQty, refineMV(0))).flatMap { stockQty =>
      refineV[NonNegative](stockQty - skuQty.quantity) match {
        case Right(remaining) =>
          (stock = stock.updated(skuQty.sku, remaining)).asRight
        case Left(insuffcientMsg) =>
          InsufficientStock(skuQty, stockQty).asLeft
      }
    })

  override def put(skuQty: SkuQuantity): F[Unit] =
    F.pure(stock.updatedWith(skuQty.sku)(current => current <+> (skuQty.quantity: NatInt).some)).void

}

class TestPublish[F[_]: Applicative] extends Publish[F] {
  var messages: Map[String, Chain[Array[Byte]]] = Map.empty

  override def publish(topic: String, msg: Array[Byte]): F[Unit] =
    F.pure(this.messages = messages.updatedWith(topic)(_ <+> Chain(msg).some))
}