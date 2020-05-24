package modesofcomposition

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object MessageResolver {

  def resolve[F[_]](msg: String): F[CustomerOrder] = ???

  def parseMsg(msg: String): Either[Error, CustomerOrderMsg] = io.circe.parser.decode[CustomerOrderMsg](msg)

  def resolveSku[F[_]: Sync](skuCode: String): F[Either[InvalidSku, Sku]] = ???

  def resolveCustomer[F[_]: Async](customerId: String): F[Either[String, CustomerId]] = ???

  def toDLQ[F[_]](msg: Json): F[Either[QueueError, Unit]] = ???

}

