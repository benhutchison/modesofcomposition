package modesofcomposition

import java.util.{Date, UUID}

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._

import io.chrisdavenport.cats.effect.time.JavaTime

import com.vladkopanev.cats.saga.Saga
import com.vladkopanev.cats.saga.Saga._


object OrderProcessor {

  def process[F[_]: Sync: RaiseErr: UuidState: Concurrent: Clock: Inventory: Publish](order: CustomerOrder): F[Unit] = {

    def takeElseReturnSku(skuQty: SkuQuantity): Saga[F, Either[InsufficientStock, Unit]] =
      F.take(skuQty).compensateIfSuccess(_ => F.put(skuQty))

    def dispatchElseBackorder[F[_]: Sync: UuidState: Clock](takes: NonEmptyChain[Either[InsufficientStock, Unit]]):
      F[Either[Backorder, Dispatched]] = {

      val allTakes: (Chain[InsufficientStock], Chain[Unit]) = takes.toChain.separate
      NonEmptyChain.fromChain(allTakes._1) match {

        case Some(insufficientStock) => for {
          time <- JavaTime[F].getInstant
          requiredStock <- insufficientStock.traverse[F, SkuQuantity] {

            case InsufficientStock(SkuQuantity(sku, required), available) =>
              refineV[Positive](required - available) match {
                case Right(pos) => F.pure(SkuQuantity(sku, pos))
                case Left(msg) => F.raiseError(new IllegalStateException(
                  s"refineT[Positive]($required - $available)"))
              }

          }
        } yield Left(Backorder(requiredStock, order, time))

        case None => for {
          time <- JavaTime[F].getInstant
          fid <- UuidRng.nextUuidState[F]
        } yield Right(Dispatched(order, time, fid))
      }
    }

    val saga = for {
      //take from inventory
      allTakes <- order.items.traverse(takeElseReturnSku)
      dispatched <- SagaExtensions.compensateE(dispatchElseBackorder(allTakes))(
        (backorder: Backorder) => F.publish("BACKORDER", backorder.asJson.toString.getBytes)
      )
      _ <- Saga.noCompensate(F.publish("DISPATCH", dispatched.asJson.toString.getBytes))
    } yield (())

    saga.transact
  }



//  def publishResponse[F[_]: Publish: ApplicativeAsk[*[_], OrderResponse => String]](response: OrderResponse): F[Unit] =
//    F.ask.>>=(topicResolver => F.publish(topicResolver(response), response.asJson.toString.getBytes))

}

trait Publish[F[_]] {
  def publish(topic: String, msg: Array[Byte]): F[Unit]
}


