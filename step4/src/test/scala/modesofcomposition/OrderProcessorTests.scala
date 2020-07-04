package modesofcomposition

import java.time.Instant

import cats.effect.concurrent.Ref

class OrderProcessorTests extends munit.FunSuite with TestSupport {

  test("processAvailableOrder - dispatch") {
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

  test("processAvailableOrder - backorder") {

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

  test("backorder") {
    val order = CustomerOrder(ausCustomer, NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))
    val insufficientStocks = NonEmptyChain(
      InsufficientStock(SkuQuantity(toyHippo, PosInt(2)), NatInt(1)))

    val actual = OrderProcessor.backorder[F](insufficientStocks, order).unsafeRunSync()

    assertEquals(actual, Backorder(NonEmptyChain(SkuQuantity(toyHippo, PosInt(1))),
      order, Instant.ofEpochMilli(currMillis)))
  }

  test("dispatch") {
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(ausCustomer, NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))
    val actual = OrderProcessor.dispatch[F](order).unsafeRunSync()

    assertEquals(actual, Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid))
  }

  test("insufficientsAndTaken") {
    val insufficientStock = InsufficientStock(SkuQuantity(toyHippo, PosInt(2)), NatInt(1))
    val take = SkuQuantity(toyKoala, PosInt(1))
    val taken = Chain(take)

    val actual = OrderProcessor.insufficientsAndTaken(
      NonEmptyChain(insufficientStock.asLeft[SkuQuantity], take.asRight[InsufficientStock]))

    assertEquals(actual, Option((NonEmptyChain(insufficientStock), taken)))
  }

}
