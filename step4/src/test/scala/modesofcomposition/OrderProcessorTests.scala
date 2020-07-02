package modesofcomposition

import java.util.UUID
import java.time.Instant

import cats.effect.concurrent.Ref

class OrderProcessorTests extends munit.FunSuite with TestSupport {

  test("processCustomerOrder - dispatch") {
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(ausCustomer,
      NonEmptyChain(SkuQuantity(toyRabbit, refineMV[Positive](1))))

    OrderProcessor.processAvailableOrder[F](order).unsafeRunSync()

    val expected = Chain(Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.getMessages(Topic.Dispatch).unsafeRunSync.
        traverse(TestSupport.fromJsonBytes[Dispatched]), expected)
  }

  test("processCustomerOrder - backorder") {

    val lowHippoStock = initialStock.updated(toyHippo, NatInt(1))

    implicit val inv = TestSupport.inventory[F](lowHippoStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(ausCustomer, NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))

    OrderProcessor.processAvailableOrder[F](order).unsafeRunSync()

    val expected = Chain(Backorder(NonEmptyChain(
      SkuQuantity(toyHippo, PosInt(1))), order, Instant.ofEpochMilli(currMillis))).asRight[io.circe.Error]

    assertEquals(publisher.getMessages(Topic.Backorder).unsafeRunSync.
      traverse(TestSupport.fromJsonBytes[Backorder](_)), expected)

    assertEquals(inv.stock, lowHippoStock)
  }

}
