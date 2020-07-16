package modesofcomposition

/** Certifies the id string refers to a Customer  */
case class Customer private[modesofcomposition](id: String, region: CustomerRegion)

/** Validates a Customer string is a valid customer */
trait CustomerLookup[F[_]] {

  def resolveCustomerId(customerId: String)(implicit F: Sync[F]): F[Either[String, Customer]]
}
object CustomerLookup {

  def apply[F[_]](implicit c: CustomerLookup[F]) = c
}
