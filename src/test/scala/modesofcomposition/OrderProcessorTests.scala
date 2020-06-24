package modesofcomposition

import java.util.UUID
import java.time.Instant

import cats.effect.concurrent.Ref

class OrderProcessorTests extends munit.FunSuite {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)

  type F[X] = IO[X]

  val seed = new UuidSeed(Array(1, 2, 3, 4))
  val rabbitCode = "Rabbit"
  val hippoCode = "Hippo"
  val toyRabbit = Sku(rabbitCode)
  val toyHippo = Sku(hippoCode)
  val skus = Chain(toyRabbit, toyHippo)
  val initialStock = Map(
    toyRabbit -> NatInt(5),
    toyHippo -> NatInt(1))

  val customerIdStr = "12345"
  val customerId = new CustomerId(customerIdStr)

  val currMillis = System.currentTimeMillis()
  implicit val clock = TestSupport.clock[F](currMillis)

  test("processMsg - dispatch") {
    implicit val skuLookup = TestSkuLookup[F](Map.from(skus.map(sku => sku.code -> sku).iterator))
    implicit val customerLookup = TestCustomerLookup[F](Map(customerIdStr -> customerId))
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val msg =
      s"""{
        |"customerId": "$customerIdStr",
        |"skuQuantities": [["${toyRabbit.code}", 2]]
        |}""".stripMargin

    OrderProcessor.processMsg[F](msg.getBytes).unsafeRunSync()

    val expected = Chain(Dispatched(new CustomerOrder(customerId,
      NonEmptyChain(SkuQuantity(toyRabbit, PosInt(2)))),
      Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.messages(OrderProcessor.TopicDispatch).traverse(TestSupport.fromJsonBytes[Dispatched]),
      expected)
  }

  test("processCustomerOrder - dispatch") {
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"),
      NonEmptyChain(SkuQuantity(toyRabbit, refineMV[Positive](1))))

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

    val expected = Chain(Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.messages(OrderProcessor.TopicDispatch).traverse(TestSupport.fromJsonBytes[Dispatched]),
      expected)
  }

  test("processCustomerOrder - backorder") {

    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(CustomerId("testcustomerid"), NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

    val expected = Chain(Backorder(NonEmptyChain(
      SkuQuantity(toyHippo, PosInt(1))), order, Instant.ofEpochMilli(currMillis))).asRight[io.circe.Error]

    assertEquals(publisher.messages(OrderProcessor.TopicBackorder).traverse(TestSupport.fromJsonBytes[Backorder](_)),
      expected)

    assertEquals(inv.stock(toyRabbit).toInt, 5)
    assertEquals(inv.stock(toyHippo).toInt, 1)
  }

}
