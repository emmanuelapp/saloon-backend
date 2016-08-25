package common.repositories.conference

import common.repositories.CollectionReferences
import common.repositories.utils.MongoDbCrudUtils
import conferences.models.{PersonId, Person}
import org.joda.time.DateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.commands.WriteResult

object PersonRepository {
  private val db = ReactiveMongoPlugin.db
  private lazy val collection: JSONCollection = db[JSONCollection](CollectionReferences.PERSONS)

  // TODO : must change when allow to edit (like conferences & presentations)
  def find(): Future[List[Person]] = MongoDbCrudUtils.find[Person](collection, Json.obj(), Json.obj("name" -> 1))
  def findByIds(ids: List[PersonId]): Future[List[Person]] = ids.headOption.map(_ => MongoDbCrudUtils.find[Person](collection, Json.obj("$or" -> ids.distinct.map(id => Json.obj("id" -> Json.obj("$eq" -> id)))), Json.obj("name" -> 1))).getOrElse(Future(List()))
  def get(pId: PersonId): Future[Option[Person]] = MongoDbCrudUtils.get[Person](collection, Json.obj("id" -> pId.unwrap))

  def insert(elt: Person): Future[WriteResult] = MongoDbCrudUtils.insert(collection, elt.copy(created = new DateTime()))
}