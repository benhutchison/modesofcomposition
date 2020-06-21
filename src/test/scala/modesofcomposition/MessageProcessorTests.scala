package modesofcomposition

import java.time.Instant

import cats.effect.concurrent.Ref

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.refined._

class MessageProcessorTests extends munit.FunSuite {

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

  test("dispatch") {
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

    MessageProcessor.process[F](msg.getBytes).unsafeRunSync()

    val expected = Chain(Dispatched(new CustomerOrder(customerId,
      NonEmptyChain(SkuQuantity(toyRabbit, PosInt(2)))),
      Instant.ofEpochMilli(currMillis), seed.uuid)).asRight[io.circe.Error]

    assertEquals(
      publisher.messages("DISPATCH").traverse(TestSupport.fromJsonBytes[Dispatched]),
      expected)
  }

}
