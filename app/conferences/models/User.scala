package conferences.models

import common.services.TwitterSrv
import play.api.data.Forms._
import play.api.libs.json.Json

case class User(
  name: String,
  email: Option[String],
  siteUrl: Option[String],
  twitter: Option[String],
  public: Boolean) {
  def trim(): User = this.copy(
    name = this.name.trim,
    email = this.email.map(_.trim),
    siteUrl = this.siteUrl.map(_.trim),
    twitter = this.twitter.map(t => TwitterSrv.toAccount(t)))
}
object User {
  implicit val format = Json.format[User]
  val fields = mapping(
    "name" -> nonEmptyText,
    "email" -> optional(nonEmptyText),
    "siteUrl" -> optional(nonEmptyText),
    "twitter" -> optional(nonEmptyText),
    "public" -> boolean
  )(User.apply)(User.unapply)
  val empty = User("", None, None, None, public = false)
}
