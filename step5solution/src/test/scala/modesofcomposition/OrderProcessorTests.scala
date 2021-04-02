package modesofcomposition

import java.util.UUID
import java.time.Instant

class OrderProcessorTests extends munit.CatsEffectSuite with TestSupport {

  test("processMsgStream") {
    implicit val skuLookup = TestSkuLookup[F](skuMap)
    implicit val customerLookup = TestCustomerLookup[F](customerMap)
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val orderStream = fs2.Stream.emits[F, Array[Byte]](Seq.fill(200)(orderJson(ausCustomerIdStr, 1, 1).getBytes()))

    OrderProcessor.processMsgStream(orderStream).compile.drain.unsafeRunSync

    val dispatchCount = publisher.getMessages(Topic.Dispatch).unsafeRunSync.size.toInt
    assert(98.to(100).contains(dispatchCount), dispatchCount)
    val backorderCount = publisher.getMessages(Topic.Backorder).unsafeRunSync.size.toInt
    assert(100.to(102).contains(backorderCount), backorderCount)
  }

}
