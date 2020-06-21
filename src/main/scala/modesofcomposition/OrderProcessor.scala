package modesofcomposition

import java.util.{Date, UUID}

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._
import io.chrisdavenport.cats.effect.time.JavaTime

object OrderProcessor {

  def process[F[_] : Functor : Async : Parallel : Clock : UuidRef: Inventory: Publish](order: CustomerOrder): F[Unit] = {
    order.items.parTraverse(F.inventoryTake).>>=(
      allTakes => dispatchElseBackorder[F](allTakes, order).>>= {
        case Right(dispatched) =>
          F.publish("DISPATCH", dispatched.asJson.toString.getBytes)
        case Left((backorder, taken)) =>
          F.publish("BACKORDER", backorder.asJson.toString.getBytes) >>
            taken.parTraverse_(F.inventoryPut)
      })

  }

  def dispatchElseBackorder[F[_]: Functor: Async: Parallel: Clock: UuidRef](
    takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]], order: CustomerOrder):
    F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

      takes.toChain.separate match {
        case (insufficients, taken) =>
          NonEmptyChain.fromChain(insufficients) match {

            case Some(insufficientStocks) =>
              (
                insufficientStocks.traverse {
                  case InsufficientStock(SkuQuantity(sku, required), available) =>
                    refineF[Positive, F](required - available).map(SkuQuantity(sku, _))
                },
                JavaTime[F].getInstant,
                ).mapN {
                case (requiredStock, time) => (Backorder(requiredStock, order, time), taken).asLeft
              }

            case None =>
              (
                JavaTime[F].getInstant,
                UuidSeed.nextUuid[F]
                ).mapN {
                case (time, id) => Dispatched(order, time, id).asRight
              }
          }
      }
  }
}

trait Publish[F[_]] {
  def publish(topic: String, msg: Array[Byte]): F[Unit]
}


