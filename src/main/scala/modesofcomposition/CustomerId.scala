package modesofcomposition

case class CustomerId private[modesofcomposition] (id: String) {

}

trait CustomerLookup[F[_]] {

  def resolveCustomerId(customerId: String)(implicit F: Async[F]): F[Either[String, CustomerId]]
}
