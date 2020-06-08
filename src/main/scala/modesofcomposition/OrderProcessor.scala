package modesofcomposition

import java.util.{Date, UUID}

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._
import io.chrisdavenport.cats.effect.time.JavaTime

object OrderProcessor {

  def process[F[_]: Functor: Async: Parallel: Clock: UuidRef](order: CustomerOrder,
                                                         inventory: Inventory[F],
                                                         publisher: Publish[F]): F[Unit] = {

    def dispatchElseBackorder(takes: NonEmptyChain[Either[InsufficientStock, Unit]]): F[OrderResponse] = {

      takes.toChain.separate match {
        case (insufficients, oks) =>
          NonEmptyChain.fromChain(insufficients) match {

            case Some(insufficientStocks) =>
              (
                insufficientStocks.traverse {
                  case InsufficientStock(SkuQuantity(sku, required), available) =>
                    refineF[Positive, F](required - available).map(SkuQuantity(sku, _))
                },
                JavaTime[F].getInstant,
              ).mapN {
                case (requiredStock, time) => Backorder(requiredStock, order, time)
              }

            case None =>
              (
                JavaTime[F].getInstant,
                UuidSeed.nextUuid[F]
              ).mapN {
                case (time, id) => Dispatched(order, time, id)
              }
          }
      }
    }

    def publishJsonBytes(topic: String, r: OrderResponse) =
      publisher.publish("BACKORDER", r.asJson.toString.getBytes)

    order.items.parTraverse(inventory.take).>>=(
    allTakes => dispatchElseBackorder(allTakes).>>= {
      case d: Dispatched =>
        publishJsonBytes("DISPATCH", d)
      case b: Backorder =>
        publishJsonBytes("BACKORDER", b) >>
        order.items.parTraverse_(inventory.put)
    })
  }

}

trait Publish[F[_]] {
  def publish(topic: String, msg: Array[Byte]): F[Unit]
}


