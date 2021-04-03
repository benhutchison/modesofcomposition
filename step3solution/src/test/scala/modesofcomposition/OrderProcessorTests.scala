package modesofcomposition

import java.util.UUID
import java.time.Instant

class OrderProcessorTests extends munit.CatsEffectSuite with TestSupport {

  test("processCustomerOrder - unavailable") {
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()

    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val order = CustomerOrder(usCustomer,
      NonEmptyChain(SkuQuantity(toyKoala, refineMV[Positive](1))))

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

    val expected = Chain(Unavailable(NonEmptySet.of(toyKoala), order, Instant.ofEpochMilli(currMillis))).asRight[io.circe.Error]

    assertEquals(
      publisher.getMessages(Topic.Unavailable).unsafeRunSync.
        traverse(TestSupport.fromJsonBytes[Unavailable]), expected)
  }

}
