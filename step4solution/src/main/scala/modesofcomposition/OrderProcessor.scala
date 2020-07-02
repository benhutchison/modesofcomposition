package modesofcomposition

import scala.collection.immutable.SortedSet

import io.chrisdavenport.cats.effect.time.JavaTime
import java.util.UUID

object OrderProcessor {

  def processAvailableOrder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory: Publish]
    (order: CustomerOrder): F[Unit] = {

    dispatchElseBackorder[F](order).>>= {
        case Right(dispatched) =>
          F.publish(Topic.Dispatch, dispatched.asJson.toString.getBytes)
        case Left((backorder, taken)) =>
          F.publish(Topic.Backorder, backorder.asJson.toString.getBytes) >>
            taken.parTraverse_(F.inventoryPut)
      }
  }

  def dispatchElseBackorder[F[_]: Sync: Parallel: Clock: UuidRef: Inventory](order: CustomerOrder):
      F[Either[(Backorder, Chain[SkuQuantity]), Dispatched]] = {

    order.items.parTraverse(F.inventoryTake).>>=(takes =>
    insufficientsAndTaken(takes) match {
          case Some((insufficientStocks, taken)) =>
            backorder(insufficientStocks, order).tupleRight(taken).map(_.asLeft)
          case None =>
            dispatch(order).map(_.asRight)
        })
    }

  def backorder[F[_] : Sync : Parallel : Clock : UuidRef]
    (insufficientStocks: NonEmptyChain[InsufficientStock], order: CustomerOrder):
    F[Backorder] = {
    (
      insufficientStocks.traverse {
        case InsufficientStock(SkuQuantity(sku, required), available) =>
          PosInt.fromF[F](required - available).map(SkuQuantity(sku, _))
      },
      JavaTime[F].getInstant,
      ).mapN {
      case (requiredStock, time) => Backorder(requiredStock, order, time)
    }
  }

  def dispatch[F[_] : Sync : Parallel : Clock : UuidRef](order: CustomerOrder): F[Dispatched] = {
    (
      JavaTime[F].getInstant,
      UuidSeed.nextUuid[F]
    ).mapN {
      case (time, id) => Dispatched(order, time, id)
    }
  }

  def insufficientsAndTaken(takes: NonEmptyChain[Either[InsufficientStock, SkuQuantity]]):
    Option[(NonEmptyChain[InsufficientStock], Chain[SkuQuantity])] = {

    val (allInsufficients, taken) = takes.toChain.separate
    NonEmptyChain.fromChain(allInsufficients).tupleRight(taken)
  }



}

