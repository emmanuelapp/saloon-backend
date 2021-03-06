package common.models.values

import common.models.utils.tString
import common.models.utils.tStringHelper
import scala.util.Try
import common.models.values.typed.GenericId

trait UUID extends Any with tString {
  def toGenericId: GenericId = GenericId(this.unwrap)
}
object UUID {
  def generate(): String = java.util.UUID.randomUUID().toString()
  def toUUID(str: String): Either[String, String] = Try(java.util.UUID.fromString(str)).toOption.map(uuid => Right(uuid.toString())).getOrElse(Left(s"Incorrect UUID <$str>"))
}
