package controllers

import common.Utils
import infrastructure.repository.EventRepository
import infrastructure.repository.SessionRepository
import infrastructure.repository.ExponentRepository
import services.MailSrv
import services.MandrillSrv
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api._
import play.api.mvc._
import play.api.libs.json.Json

object Application extends Controller {

  def home = Action { implicit req =>
    Ok(views.html.Application.home())
  }
  def sample = Action { implicit req =>
    Ok(views.html.Application.sample())
  }

  //def migrate = TODO
  def migrate = Action.async {
    for {
      m1 <- migrateEvents()
      //m2 <- migrateSessions()
      //m3 <- migrateExponents()
    } yield {
      Redirect(routes.Application.home).flashing("success" -> "Migrated !")
    }
  }
  private def migrateEvents(): Future[List[Option[models.Event]]] = {
    EventRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      EventRepository.update(e.uuid, e.transform())
    }))
  }
  /*private def migrateSessions(): Future[List[Option[models.Session]]] = {
    SessionRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      SessionRepository.update(e.uuid, e.transform())
    }))
  }
  private def migrateExponents(): Future[List[Option[models.Exponent]]] = {
    ExponentRepository.findAllOld().flatMap(list => Future.sequence(list.map { e =>
      ExponentRepository.update(e.uuid, e.transform())
    }))
  }*/

  def corsPreflight(all: String) = Action {
    Ok("").withHeaders(
      "Allow" -> "*",
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Origin, X-Requested-With, Content-Type, Accept, Referrer, User-Agent, userId, timestamp");
  }
}
