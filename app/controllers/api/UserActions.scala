package controllers.api

import infrastructure.repository.common.Repository
import infrastructure.repository.EventRepository
import infrastructure.repository.EventItemRepository
import infrastructure.repository.UserRepository
import infrastructure.repository.UserActionRepository
import models.Event
import models.EventUI
import models.EventItem
import models.User
import models.UserAction
import org.joda.time.DateTime
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api._
import play.api.mvc._
import play.api.libs.json._
import reactivemongo.core.commands.LastError

object UserActions extends Controller {

  def favorite(eventId: String, itemType: String, itemId: String) = Action.async { implicit req =>
    setActionUnique(eventId, itemType, itemId)(UserActionRepository.getFavorite, UserActionRepository.insertFavorite)
  }

  def unfavorite(eventId: String, itemType: String, itemId: String) = Action.async { implicit req =>
    deleteActionUnique(eventId, itemType, itemId)(UserActionRepository.deleteFavorite)
  }

  def done(eventId: String, itemType: String, itemId: String) = Action.async { implicit req =>
    setActionUnique(eventId, itemType, itemId)(UserActionRepository.getDone, UserActionRepository.insertDone)
  }

  def undone(eventId: String, itemType: String, itemId: String) = Action.async { implicit req =>
    deleteActionUnique(eventId, itemType, itemId)(UserActionRepository.deleteDone)
  }

  def mood(eventId: String, itemType: String, itemId: String) = Action.async(parse.json) { implicit req =>
    bodyWith("rating") { rating =>
      withUser() { user =>
        withData(eventId, itemType, itemId) { (event, item) =>
          UserActionRepository.getMood(user.uuid, itemType, item.uuid).flatMap { moodOpt =>
            UserActionRepository.setMood(user.uuid, itemType, item.uuid, eventId, moodOpt, rating, withTime()).map { eltOpt =>
              eltOpt.map(elt => Ok(Json.toJson(elt))).getOrElse(InternalServerError)
            }
          }
        }
      }
    }
  }

  def createComment(eventId: String, itemType: String, itemId: String) = Action.async(parse.json) { implicit req =>
    bodyWith("text") { text =>
      withUser() { user =>
        withData(eventId, itemType, itemId) { (event, item) =>
          UserActionRepository.insertComment(user.uuid, itemType, item.uuid, text, event.uuid, withTime()).map { eltOpt =>
            eltOpt.map(elt => Created(Json.toJson(elt))).getOrElse(InternalServerError)
          }
        }
      }
    }
  }

  def updateComment(eventId: String, itemType: String, itemId: String, uuid: String) = Action.async(parse.json) { implicit req =>
    bodyWith("text") { text =>
      withUser() { user =>
        withData(eventId, itemType, itemId) { (event, item) =>
          UserActionRepository.getComment(user.uuid, itemType, item.uuid, uuid).flatMap {
            _.map { userAction =>
              UserActionRepository.updateComment(user.uuid, itemType, item.uuid, uuid, userAction, text, withTime()).map { eltOpt =>
                eltOpt.map(elt => Ok(Json.toJson(elt))).getOrElse(InternalServerError)
              }
            }.getOrElse(Future(NotFound(Json.obj("message" -> s"Unable to find comment with id $uuid"))))
          }
        }
      }
    }
  }

  def deleteComment(eventId: String, itemType: String, itemId: String, uuid: String) = Action.async { implicit req =>
    withUser() { user =>
      withData(eventId, itemType, itemId) { (event, item) =>
        UserActionRepository.deleteComment(user.uuid, itemType, item.uuid, uuid).map { lastError =>
          if (lastError.ok) { if (lastError.n == 0) NotFound(Json.obj("message" -> s"Unable to find comment with id $uuid")) else NoContent } else { InternalServerError }
        }
      }
    }
  }

  def subscribe(eventId: String): Action[JsValue] = subscribe(eventId, EventUI.className, eventId)
  def subscribe(eventId: String, itemType: String, itemId: String) = Action.async(parse.json) { implicit req =>
    bodyWith("email", "filter") { (email, filter) =>
      setActionUnique(eventId, itemType, itemId)(UserActionRepository.getSubscribe, UserActionRepository.insertSubscribe(email, filter))
    }
  }

  def unsubscribe(eventId: String): Action[AnyContent] = unsubscribe(eventId, EventUI.className, eventId)
  def unsubscribe(eventId: String, itemType: String, itemId: String) = Action.async { implicit req =>
    deleteActionUnique(eventId, itemType, itemId)(UserActionRepository.deleteSubscribe)
  }

  private def setActionUnique(eventId: String, itemType: String, itemId: String)(get: (String, String, String) => Future[Option[UserAction]], insert: (String, String, String, String, Option[DateTime]) => Future[Option[UserAction]])(implicit req: Request[Any]): Future[Result] = {
    withUser() { user =>
      withData(eventId, itemType, itemId) { (event, item) =>
        get(user.uuid, itemType, item.uuid).flatMap {
          _.map { elt => Future(Ok(Json.toJson(elt))) }.getOrElse {
            insert(user.uuid, itemType, item.uuid, event.uuid, withTime()).map { eltOpt =>
              eltOpt.map(elt => Created(Json.toJson(elt))).getOrElse(InternalServerError)
            }
          }
        }
      }
    }
  }

  private def deleteActionUnique(eventId: String, itemType: String, itemId: String)(delete: (String, String, String) => Future[LastError])(implicit req: Request[Any]): Future[Result] = {
    withUser() { user =>
      withData(eventId, itemType, itemId) { (event, item) =>
        delete(user.uuid, itemType, item.uuid).map { lastError =>
          if (lastError.ok) { NoContent } else { InternalServerError }
        }
      }
    }
  }

  private def bodyWith(field: String)(exec: (String) => Future[Result])(implicit req: Request[JsValue]) = {
    (req.body \ field).asOpt[String].map { value =>
      exec(value)
    }.getOrElse(Future(BadRequest(Json.obj("message" -> s"Your request body should have a JSON object with a field '$field' !"))))
  }

  private def bodyWith(field1: String, field2: String)(exec: (String, String) => Future[Result])(implicit req: Request[JsValue]) = {
    (for {
      value1 <- (req.body \ field1).asOpt[String]
      value2 <- (req.body \ field2).asOpt[String]
    } yield {
      exec(value1, value2)
    }).getOrElse(Future(BadRequest(Json.obj("message" -> s"Your request body should have a JSON object with fields '$field1' & '$field2' !"))))
  }

  private def withUser()(exec: (User) => Future[Result])(implicit req: Request[Any]) = {
    req.headers.get("userId").map { userId =>
      UserRepository.getByUuid(userId).flatMap {
        _.map { user => exec(user) }.getOrElse(Future(NotFound(Json.obj("message" -> s"User <$userId> not found !"))))
      }
    }.getOrElse(Future(BadRequest(Json.obj("message" -> "You should set 'userId' header !"))))
  }

  private def withTime()(implicit req: Request[Any]): Option[DateTime] = {
    req.headers.get("timestamp").map(t => new DateTime(t.toLong))
  }

  private def withData(eventId: String, itemType: String, itemId: String)(exec: (Event, EventItem) => Future[Result])(implicit req: Request[Any]) = {
    val futureData = for {
      event <- EventRepository.getByUuid(eventId)
      item <- EventItemRepository.getByUuid(itemType, itemId)
    } yield (event, item)

    futureData.flatMap {
      case (event, item) =>
        if (event.isDefined && item.isDefined) {
          exec(event.get, item.get)
        } else {
          val notFounds = List(
            event.map(_ => None).getOrElse(Some(s"Event <$eventId>")),
            item.map(_ => None).getOrElse(Some(s"<$itemType> <$itemId>"))).flatten
          Future(NotFound(Json.obj("message" -> s"Not found : ${notFounds.mkString(", ")}")))
        }
    }
  }
}
