package backend.forms

import common.models.utils.tStringConstraints._
import common.models.values.typed.Email
import common.models.values.typed.FirstName
import common.models.values.typed.LastName
import common.models.user.User
import common.models.user.UserId
import common.models.user.UserInfo
import play.api.data.Forms._
import org.joda.time.DateTime
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers.CredentialsProvider

case class UserData(
  firstName: FirstName,
  lastName: LastName)
object UserData {
  val fields = mapping(
    "firstName" -> of[FirstName].verifying(nonEmpty),
    "lastName" -> of[LastName].verifying(nonEmpty))(UserData.apply)(UserData.unapply)

  def toInfo(d: UserData): UserInfo = UserInfo(d.firstName, d.lastName)
  def toModel(d: UserData): User = User(UserId.generate(), List(), LoginInfo(CredentialsProvider.Credentials, ""), Email(""), UserInfo(d.firstName, d.lastName))
  def fromModel(d: User): UserData = UserData(d.info.firstName, d.info.lastName)
  def merge(m: User, d: UserData): User = m.copy(info = toInfo(d), meta = m.meta.copy(updated = new DateTime()))
}