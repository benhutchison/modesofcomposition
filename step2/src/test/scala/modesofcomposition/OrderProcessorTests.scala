package modesofcomposition

import java.util.UUID
import java.time.Instant

import cats.effect.concurrent.Ref

class OrderProcessorTests extends munit.FunSuite with TestSupport {

  test("resolveOrderMsg") {
    implicit val skuLookup = TestSkuLookup[F](skuMap)
    implicit val customerLookup = TestCustomerLookup[F](customerMap)

    val orderMsg = OrderMsg(usCustomerIdStr, NonEmptyChain(
      (koalaCode, 1),
      (hippoCode, 2),
    ))

    val order = OrderProcessor.resolveOrderMsg[F](orderMsg).unsafeRunSync()

    val expected = new CustomerOrder(usCustomer,
      NonEmptyChain(
        SkuQuantity(toyKoala, PosInt(1)),
        SkuQuantity(toyHippo, PosInt(2)),
      ))

    assertEquals(order, expected)
  }

}
