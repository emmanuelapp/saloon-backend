package models

import common.infrastructure.repository.Repository
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.libs.json.Json

case class User(
  uuid: String,
  device: Device,
  push: Option[Push],
  saloonMemo: String,
  created: DateTime,
  updated: DateTime)
object User {
  implicit val format = Json.format[User]
  def fromDevice(device: Device): User = User(Repository.generateUuid(), device, None, "", new DateTime(), new DateTime())
}

// mapping object for User Form
case class UserData(
  device: Device,
  push: Option[Push],
  saloonMemo: String)
object UserData {
  implicit val format = Json.format[UserData]
  val fields = mapping(
    "device" -> Device.fields,
    "push" -> optional(Push.fields),
    "saloonMemo" -> text)(UserData.apply)(UserData.unapply)

  def toModel(d: UserData): User = User(Repository.generateUuid(), d.device, d.push, d.saloonMemo, new DateTime(), new DateTime())
  def fromModel(m: User): UserData = UserData(m.device, m.push, m.saloonMemo)
  def merge(m: User, d: UserData): User = m.copy(device = d.device, push = d.push, saloonMemo = d.saloonMemo, updated = new DateTime())
}
