package admin.controllers

import common.Utils
import common.models.event.Event
import common.models.event.Attendee
import common.models.event.Session
import common.models.event.Exponent
import common.models.user.User
import common.models.user.Device
import common.repositories.event.EventRepository
import common.repositories.event.AttendeeRepository
import common.repositories.event.SessionRepository
import common.repositories.event.ExponentRepository
import common.repositories.user.UserRepository
import common.repositories.user.DeviceRepository
import common.services.EmailSrv
import authentication.environments.SilhouetteEnvironment
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api._
import play.api.mvc._
import play.api.libs.json.Json

object Application extends SilhouetteEnvironment {

  def index = SecuredAction { implicit req =>
    Ok(admin.views.html.home())
  }
  def sample = Action { implicit req =>
    Ok(admin.views.html.sample())
  }

  def migrate = TODO
  /*def migrate = Action.async {
    for {
      m1 <- migrateUsers()
      m2 <- migrateEvents()
      m3 <- migrateAttendees()
      m4 <- migrateExponents()
      m5 <- migrateSessions()
    } yield {
      Redirect(routes.Application.index).flashing("success" -> "Migrated !")
    }
  }
  private def migrateUsers(): Future[List[Option[User]]] = {
    UserRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      UserRepository.update(e.uuid, e.transform())
    }))
  }
  private def migrateEvents(): Future[List[Option[Event]]] = {
    EventRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      EventRepository.update(e.uuid, e.transform())
    }))
  }
  private def migrateAttendees(): Future[List[Option[Attendee]]] = {
    AttendeeRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      AttendeeRepository.update(e.uuid, e.transform())
    }))
  }
  private def migrateExponents(): Future[List[Option[Exponent]]] = {
    ExponentRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      ExponentRepository.update(e.uuid, e.transform())
    }))
  }
  private def migrateSessions(): Future[List[Option[Session]]] = {
    SessionRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      SessionRepository.update(e.uuid, e.transform())
    }))
  }*/

}
