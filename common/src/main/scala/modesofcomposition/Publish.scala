package modesofcomposition

/** Models a topic-oriented message publication service */
trait Publish[F[_]] {

  /** Sends msg bytes to a named topic */
  def publish(topic: String, msg: Array[Byte]): F[Unit]
}
