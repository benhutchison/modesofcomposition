package modesofcomposition

import java.nio.charset.StandardCharsets

trait CirceSupport {

  implicit class JsonBytes[A: Encoder](domain: A) {
    def asJsonBytes: Array[Byte] = domain.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }
}