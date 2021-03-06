package authentication.repositories.impl

import common.repositories.CollectionReferences
import authentication.repositories.PasswordRepository
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.Play.current
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.JsObjectDocumentWriter
import play.modules.reactivemongo.json.collection.JSONCollection
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers.PasswordInfo

object MongoPasswordRepository extends PasswordRepository {
  val db = ReactiveMongoPlugin.db
  lazy val collection: JSONCollection = db[JSONCollection](CollectionReferences.CREDENTIALS)
  implicit val formatLoginInfo = Json.format[LoginInfo]
  implicit val formatPasswordInfo = Json.format[PasswordInfo]

  def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
    collection.find(Json.obj("loginInfo" -> loginInfo)).one[JsValue].map(_.map(json => (json \ "passwordInfo").as[PasswordInfo]))
  }

  def save(loginInfo: LoginInfo, passwordInfo: PasswordInfo): Future[PasswordInfo] = {
    collection.update(Json.obj("loginInfo" -> loginInfo), Json.obj("loginInfo" -> loginInfo, "passwordInfo" -> passwordInfo), upsert = true).map { err =>
      passwordInfo
    }
  }

  def remove(loginInfo: LoginInfo): Future[WriteResult] = collection.remove(Json.obj("loginInfo" -> loginInfo))
}
