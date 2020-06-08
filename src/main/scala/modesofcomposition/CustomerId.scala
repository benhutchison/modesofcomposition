package modesofcomposition

case class CustomerId private (id: String) {

}

trait CustomerLookup[F[_]] {

  def resolve(customerId: String)(implicit F: Async[F]): F[Either[String, CustomerId]]
}
