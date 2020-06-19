package modesofcomposition

import java.time.Instant

import cats.effect.concurrent.Ref
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._

import alleycats.std.map._

import scala.collection.SortedMap

class OrderProcessorTests extends munit.FunSuite {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)

  type F[X] = IO[X]

  val seed = new UuidSeed(Array(1, 2, 3, 4))
  val toyRabbit = Sku("Rabbit")
  val toyHippo = Sku("Hippo")
  val initialStock = Map(
    toyRabbit -> NatInt(5),
    toyHippo -> NatInt(1))
  val currMillis = System.currentTimeMillis()
  implicit val clock = TestSupport.clock[F](currMillis)

  test("dispatch") {
    val inv = TestSupport.inventory[F](initialStock)
    val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"),
      NonEmptyChain(SkuQuantity(toyRabbit, refineMV[Positive](1))))

    OrderProcessor.process[F](order, inv, publisher).unsafeRunSync()

    val expected = Chain(Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.messages("DISPATCH").traverse(TestSupport.fromJsonBytes[Dispatched]),
      expected)
  }

  test("backorder") {

    val inv = TestSupport.inventory[F](initialStock)
    val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"), NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))

    OrderProcessor.process[F](order, inv, publisher).unsafeRunSync()

    val expected = Chain(Backorder(NonEmptyChain(
      SkuQuantity(toyHippo, PosInt(1))), order, Instant.ofEpochMilli(currMillis))).asRight[io.circe.Error]

    assertEquals(publisher.messages("BACKORDER").traverse(TestSupport.fromJsonBytes[Backorder](_)),
      expected)

    assertEquals(inv.stock(toyRabbit).toInt, 5)
    assertEquals(inv.stock(toyHippo).toInt, 1)
  }

}
