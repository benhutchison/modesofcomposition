package modesofcomposition

import java.util.UUID
import java.time.Instant

import cats.effect.concurrent.Ref

class OrderProcessorTests extends munit.FunSuite with TestSupport {



  test("processMsgStream") {
    implicit val skuLookup = TestSkuLookup[F](Map.from(skus.map(sku => sku.code -> sku).iterator))
    implicit val customerLookup = TestCustomerLookup[F](Map(ausCustomerIdStr -> ausCustomer))
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val orderStream = fs2.Stream.emits[F, Array[Byte]](Seq.fill(200)(orderJson(ausCustomerIdStr, 1, 1).getBytes()))

    OrderProcessor.processMsgStream(orderStream).compile.drain.unsafeRunSync

    val dispatchCount = publisher.getMessages(OrderProcessor.TopicDispatch).unsafeRunSync.size.toInt
    assert(98.to(100).contains(dispatchCount), dispatchCount)
    val backorderCount = publisher.getMessages(OrderProcessor.TopicBackorder).unsafeRunSync.size.toInt
    assert(100.to(102).contains(backorderCount), backorderCount)
  }

  test("processMsg - dispatch") {
    implicit val skuLookup = TestSkuLookup[F](Map.from(skus.map(sku => sku.code -> sku).iterator))
    implicit val customerLookup = TestCustomerLookup[F](Map(ausCustomerIdStr -> ausCustomer))
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val msg =
      s"""{
        |"customerId": "$ausCustomerIdStr",
        |"skuQuantities": [["${toyRabbit.code}", 2]]
        |}""".stripMargin

    OrderProcessor.processMsg[F](msg.getBytes).unsafeRunSync()

    val expected = Chain(Dispatched(new CustomerOrder(ausCustomer,
      NonEmptyChain(SkuQuantity(toyRabbit, PosInt(2)))),
      Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.getMessages(OrderProcessor.TopicDispatch).unsafeRunSync.
        traverse(TestSupport.fromJsonBytes[Dispatched]), expected)
  }

  test("processCustomerOrder - dispatch") {
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(ausCustomer,
      NonEmptyChain(SkuQuantity(toyRabbit, refineMV[Positive](1))))

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

    val expected = Chain(Dispatched(order, Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.getMessages(OrderProcessor.TopicDispatch).unsafeRunSync.
        traverse(TestSupport.fromJsonBytes[Dispatched]), expected)
  }

  test("processCustomerOrder - backorder") {

    val initialStock = Map(
      toyRabbit -> NatInt(500),
      toyHippo -> NatInt(1),
      toyKoala -> NatInt(200),
    )

    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(ausCustomer, NonEmptyChain(
      SkuQuantity(toyRabbit, PosInt(1)),
      SkuQuantity(toyHippo, PosInt(2)),
    ))

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

    val expected = Chain(Backorder(NonEmptyChain(
      SkuQuantity(toyHippo, PosInt(1))), order, Instant.ofEpochMilli(currMillis))).asRight[io.circe.Error]

    assertEquals(publisher.getMessages(OrderProcessor.TopicBackorder).unsafeRunSync.
      traverse(TestSupport.fromJsonBytes[Backorder](_)), expected)

    assertEquals(inv.stock, initialStock)
  }

}
