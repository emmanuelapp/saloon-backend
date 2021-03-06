package admin.services

import common.models.values.typed.FullName
import common.models.values.typed.WebsiteUrl
import common.models.event.Event
import common.models.event.EventId
import common.models.event.Attendee
import common.models.event.AttendeeId
import common.models.event.Exponent
import common.models.event.ExponentId
import common.models.event.Session
import common.models.event.SessionId
import common.repositories.event.EventRepository
import common.repositories.event.AttendeeRepository
import common.repositories.event.SessionRepository
import common.repositories.event.ExponentRepository
import api.controllers.compatibility.Writer
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.mvc.RequestHeader
import org.joda.time.DateTime

object EventImport {

  def fetchFullEvent(url: String)(implicit req: RequestHeader): Future[Option[(Event, List[Attendee], List[Session], List[Exponent])]] = {
    val realUrl = if (url.startsWith("http")) url else "http://" + req.host + url
    WS.url(realUrl).get().map { response =>
      response.json.asOpt[Event].map { event =>
        val attendees = (response.json \ "attendees").as[List[Attendee]]
        val sessions = (response.json \ "sessions").as[List[Session]]
        val exponents = (response.json \ "exponents").as[List[Exponent]]
        (event, attendees, sessions, exponents)
      }
    }
  }

  def fetchEvent(remoteHost: String, eventId: EventId, generateIds: Boolean)(implicit req: RequestHeader): Future[Option[(Event, List[Attendee], List[Session], List[Exponent])]] = {
    val localUrl = api.controllers.routes.Events.detailsFull(eventId, Writer.lastVersion).absoluteURL(true)
    val remoteUrl = localUrl.replace(req.host, remoteHost)
    WS.url(remoteUrl).get().map { response =>
      response.json.asOpt[Event].map { event =>
        val attendees = (response.json \ "attendees").as[List[Attendee]]
        val sessions = (response.json \ "sessions").as[List[Session]]
        val exponents = (response.json \ "exponents").as[List[Exponent]]

        if (generateIds) {
          val newEventId = EventId.generate()
          (event.copy(uuid = newEventId),
            attendees.map(_.copy(uuid = AttendeeId.generate(), eventId = newEventId)),
            sessions.map(_.copy(uuid = SessionId.generate(), eventId = newEventId)),
            exponents.map(_.copy(uuid = ExponentId.generate(), eventId = newEventId)))
        } else {
          (event, attendees, sessions, exponents)
        }
      }
    }
  }

  def insertAll(event: Event, attendees: List[Attendee], sessions: List[Session], exponents: List[Exponent]): Future[Option[(Event, Int, Int, Int)]] = {
    EventRepository.insert(event).flatMap {
      _.map { inserted =>
        for {
          attendeeCount <- AttendeeRepository.bulkInsert(attendees)
          sessionCount <- SessionRepository.bulkInsert(sessions)
          exponentCount <- ExponentRepository.bulkInsert(exponents)
        } yield Some((inserted, attendeeCount.n, sessionCount.n, exponentCount.n))
      }.getOrElse(Future(None))
    }
  }

  def attendeeDiff(oldElts: List[Attendee], newElts: List[Attendee]): (List[Attendee], List[Attendee], List[(Attendee, Attendee)]) = {
    diff(oldElts, newElts, (s: Attendee) => s.name, (s: Attendee) => s.meta.source.map(_.ref), (os: Attendee, ns: Attendee) => os.merge(ns), (os: Attendee, ns: Attendee) => os.copy(meta = os.meta.copy(updated = new DateTime(0))) == ns.copy(meta = ns.meta.copy(updated = new DateTime(0))))
  }

  def sessionDiff(oldElts: List[Session], newElts: List[Session]): (List[Session], List[Session], List[(Session, Session)]) = {
    diff(oldElts, newElts, (s: Session) => s.name, (s: Session) => s.meta.source.map(_.ref), (os: Session, ns: Session) => os.merge(ns), (os: Session, ns: Session) => os.copy(meta = os.meta.copy(updated = new DateTime(0))) == ns.copy(meta = ns.meta.copy(updated = new DateTime(0))))
  }

  def exponentDiff(oldElts: List[Exponent], newElts: List[Exponent]): (List[Exponent], List[Exponent], List[(Exponent, Exponent)]) = {
    diff(oldElts, newElts, (e: Exponent) => e.name, (e: Exponent) => e.meta.source.map(_.ref), (oe: Exponent, ne: Exponent) => oe.merge(ne), (oe: Exponent, ne: Exponent) => oe.copy(meta = oe.meta.copy(updated = new DateTime(0))) == ne.copy(meta = ne.meta.copy(updated = new DateTime(0))))
  }

  private def diff[A](oldElts: List[A], newElts: List[A], getName: A => FullName, getRef: A => Option[String], merge: (A, A) => A, equals: (A, A) => Boolean): (List[A], List[A], List[(A, A)]) = {
    val createdElts = newElts.filter(ne => oldElts.find(oe => eqOpt(getRef(oe), getRef(ne)) || getName(oe) == getName(ne)).isEmpty)
    val deletedElts = oldElts.filter(oe => newElts.find(ne => eqOpt(getRef(oe), getRef(ne)) || getName(oe) == getName(ne)).isEmpty)
    val updatedElts = oldElts
      .map(oe => newElts.find(ne => eqOpt(getRef(oe), getRef(ne)) || getName(oe) == getName(ne)).map(ne => (oe, ne))).flatten
      .map { case (oe, ne) => (oe, merge(oe, ne)) }
      .filter { case (oe, ne) => !equals(oe, ne) }
    (createdElts, deletedElts, updatedElts)
  }

  private def eqOpt[A](opt1: Option[A], opt2: Option[A]): Boolean = {
    opt1.map { value1 =>
      opt2.map { _ == value1 }.getOrElse(false)
    }.getOrElse(false)
  }

  private val eventUrlMatcher = """https?://(.+\.herokuapp.com)/events/([0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12})""".r
  def formatUrl(url: WebsiteUrl)(implicit req: RequestHeader): String = {
    url.unwrap match {
      case eventUrlMatcher(remoteHost, eventId) => {
        val localUrl = api.controllers.routes.Events.detailsFull(EventId(eventId), Writer.lastVersion).absoluteURL(true)
        localUrl.replace(req.host, remoteHost)
      }
      case _ => url.unwrap
    }
  }
}