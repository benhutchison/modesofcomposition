package modesofcomposition

import java.time.Instant

import cats.effect.concurrent.Ref
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._

class OrderProcessorTests extends munit.FunSuite {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)

  type F[X] = IO[X]

  val seed = new UuidSeed(Array(1, 2, 3, 4))
  val toyRabbit = Sku("Rabbit")
  val toyHippo = Sku("Hippo")
  val currMillis = System.currentTimeMillis()
  implicit val clock = TestSupport.clock[F](currMillis)

  test("dispatch") {
    val inv = TestSupport.inventory[F](Map(toyRabbit -> refineMV[NonNegative](5)))
    val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"),
      NonEmptyChain(SkuQuantity(toyRabbit, refineMV[Positive](1))))

    OrderProcessor.process[F](order, inv, publisher).unsafeRunSync()

    val expected = Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid)

    assertEquals(TestSupport.fromJsonBytes[Dispatched](publisher.messages("DISPATCH").headOption.get), expected)
  }

  test("backorder") {

    val inv = TestSupport.inventory[F](Map(
      toyRabbit -> refineMV[NonNegative](5), toyHippo -> refineMV[NonNegative](1)))
    val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"), NonEmptyChain(
      SkuQuantity(toyRabbit, refineMV[Positive](1)),
      SkuQuantity(toyHippo, refineMV[Positive](2)),
    ))

    OrderProcessor.process[F](order, inv, publisher).unsafeRunSync()

    val expected = Backorder(NonEmptyChain(SkuQuantity(toyHippo, refineMV[Positive](1))), order, Instant.ofEpochMilli(currMillis))

    assertEquals(TestSupport.fromJsonBytes[Backorder](publisher.messages("BACKORDER").headOption.get), expected)

    assertEquals(inv.stock(toyRabbit).toInt, 5)
    assertEquals(inv.stock(toyHippo).toInt, 1)
  }
}
