package modesofcomposition

import java.util.UUID
import java.time.Instant

import scala.collection.mutable.ArrayBuffer
import cats.effect.concurrent.Ref

import scala.annotation.tailrec
import scala.collection.mutable

class OrderProcessorTests extends munit.FunSuite with TestSupport {

  def intersperseN[F[_], A](s: fs2.Stream[F, A], separator: A, n: Int): fs2.Stream[F, A] = {
    def goNextIntersperse(src: fs2.Stream[F, A], remain: Chunk[A], nRemain: Int): Pull[F, A, Unit] = {
      val rs = remain.size
      if (nRemain <= rs) {
        val (curr, next) = remain.splitAt(nRemain)
        Pull.output(Chunk.buffer(new ArrayBuffer[A](nRemain + 1)
          .appendAll(curr.iterator)
          .append(separator))) >>
          goNextIntersperse(src, next, n)
      } else {
        Pull.output(remain) >>
          src.pull.uncons.flatMap {
            case Some((nextChunk, tl)) =>
              val nRemainNextChunk = nRemain - rs
              val (curr, next) = nextChunk.splitAt(nRemainNextChunk)
              val cs = curr.size
              val nRemainAfterNextChunk = nRemainNextChunk - cs
              if (nRemainAfterNextChunk <= 0)
                Pull.output(Chunk.buffer(new ArrayBuffer[A](cs + 1)
                  .appendAll(curr.iterator)
                  .append(separator))) >>
                  goNextIntersperse(tl, next, n)
              else
                Pull.output(curr) >>
                  goNextIntersperse(tl, next, nRemainAfterNextChunk)

            case None =>
              Pull.output(remain) >> Pull.done
          }
      }
    }
    require(n >= 1)
    goNextIntersperse(s, Chunk.empty[A], n).stream
  }

  test("intersperseN") {
    Seq(3, 5, 8, 18).foreach {
      case chunkLimit =>
        val ys = fs2.Stream.emits(Seq.fill(1000)("Y")).chunkLimit(chunkLimit).flatMap(c => fs2.Stream.chunk(c))
        val yns = intersperseN(ys, "N", 5)

        assertEquals(
          yns.toList.take(15),
          List("Y", "Y", "Y", "Y", "Y", "N", "Y", "Y", "Y", "Y", "Y", "N", "Y", "Y", "Y")
        )
    }
  }

  test("processMsgStream") {
    implicit val skuLookup = TestSkuLookup[F](skuMap)
    implicit val customerLookup = TestCustomerLookup[F](customerMap)
    implicit val inv = TestSupport.inventory[F](initialStock)
    implicit val publisher = new TestPublish[F]()
    implicit val ref = Ref.unsafe[F, UuidSeed](seed)

    val orderStream = fs2.Stream.emits[F, Array[Byte]](Seq.fill(200)(
      orderJson(ausCustomerIdStr, 1, 1).getBytes()))

    OrderProcessor.processMsgStream(orderStream).compile.drain.unsafeRunSync

    val dispatchCount = publisher.getMessages(Topic.Dispatch).unsafeRunSync.size.toInt
    assert(98.to(100).contains(dispatchCount), dispatchCount)
    val backorderCount = publisher.getMessages(Topic.Backorder).unsafeRunSync.size.toInt
    assert(100.to(102).contains(backorderCount), backorderCount)
  }

  test("processMsg - dispatch") {
    implicit val skuLookup = TestSkuLookup[F](skuMap)
    implicit val customerLookup = TestCustomerLookup[F](customerMap)
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
      publisher.getMessages(Topic.Dispatch).unsafeRunSync.
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

    OrderProcessor.processCustomerOrder[F](order).unsafeRunSync()

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
