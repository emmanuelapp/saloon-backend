package authentication.repositories.impl

import common.models.user.User
import common.repositories.CollectionReferences
import authentication.repositories.UserRepository
import authentication.repositories.UserCreationException
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

object MongoUserRepository extends UserRepository {
  val db = ReactiveMongoPlugin.db
  lazy val collection: JSONCollection = db[JSONCollection](CollectionReferences.USERS)
  implicit val formatLoginInfo = Json.format[LoginInfo]

  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    collection.find(Json.obj("loginInfo" -> loginInfo)).one[User]
  }

  def save(user: User) = {
    collection.find(Json.obj("email" -> user.email)).one[User].flatMap {
      _.map { existingUser => // user exists
        //Future.failed(new UserCreationException("user email already exists."))
        Future(existingUser)
      }.getOrElse { // user does not exists
        collection.save(user).map { err =>
          user
        }
      }
    }
  }

  def remove(loginInfo: LoginInfo): Future[WriteResult] = collection.remove(Json.obj("loginInfo" -> loginInfo))
}
