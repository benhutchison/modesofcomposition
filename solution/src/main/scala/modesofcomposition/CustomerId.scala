package modesofcomposition

/** Certifies the id string refers to a Customer  */
case class CustomerId private[modesofcomposition] (id: String)

/** Validates a CustomerId string is a valid customer */
trait CustomerLookup[F[_]] {

  def resolveCustomerId(customerId: String)(implicit F: Async[F]): F[Either[String, CustomerId]]
}
