package modesofcomposition

/** Models a topic-oriented message publication service */
trait Publish[F[_]] {

  /** Sends msg bytes to a named topic */
  def publish(topic: String, msg: Array[Byte]): F[Unit]
}
object Publish {

  def apply[F[_]](implicit p: Publish[F]) = p
}

object Topic {
  val Dispatch = "DISPATCH"
  val Backorder = "BACKORDER"
  val Deadletter = "DEADLETTER"
  val Unavailable = "UNAVAILABLE"
}
