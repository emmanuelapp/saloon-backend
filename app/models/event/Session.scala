package models.event

import common.Utils
import common.infrastructure.repository.Repository
import models.values.DataSource
import services.FileImporter
import org.joda.time.DateTime
import scala.util.Try
import play.api.data.Forms._
import play.api.libs.json.Json

case class SessionOld(
  uuid: String,
  eventId: String,
  name: String,
  description: String,
  format: String,
  category: String,
  place: String, // where to find this exponent
  start: Option[DateTime],
  end: Option[DateTime],
  speakers: List[Attendee],
  tags: List[String],
  slides: Option[String],
  video: Option[String],
  source: Option[DataSource], // where the session were fetched (if applies)
  created: DateTime,
  updated: DateTime) {
  def transform(): Session = Session(
    this.uuid,
    this.eventId,
    this.name,
    this.description,
    SessionImages(
      ""),
    SessionInfo(
      this.format,
      this.category,
      this.place,
      this.start,
      this.end,
      this.speakers,
      this.slides,
      this.video),
    SessionMeta(
      this.source.map(s => s.copy(name = s.name.orElse(Some("")))),
      this.created,
      this.updated))
}
object SessionOld {
  implicit val format = Json.format[SessionOld]
}

case class SessionImages(
  landing: String) // landscape img (~ 400x150)
case class SessionInfo(
  format: String,
  category: String,
  place: String, // where to find this session
  start: Option[DateTime],
  end: Option[DateTime],
  speakers: List[Attendee],
  slides: Option[String],
  video: Option[String])
case class SessionMeta(
  source: Option[DataSource], // where the session were fetched (if applies)
  created: DateTime,
  updated: DateTime)
case class Session(
  uuid: String,
  eventId: String,
  name: String,
  description: String,
  images: SessionImages,
  info: SessionInfo,
  meta: SessionMeta) extends EventItem {
  def toMap(): Map[String, String] = Session.toMap(this)
  def merge(e: Session): Session = Session.merge(this, e)
}
object Session {
  val className = "sessions"
  implicit val formatSessionImages = Json.format[SessionImages]
  implicit val formatSessionInfo = Json.format[SessionInfo]
  implicit val formatSessionMeta = Json.format[SessionMeta]
  implicit val format = Json.format[Session]
  private def parseDate(date: String) = Utils.parseDate(FileImporter.dateFormat)(date)

  def fromMap(eventId: String)(d: Map[String, String]): Try[Session] =
    Try(Session(
      d.get("uuid").flatMap(u => if (u.isEmpty) None else Some(u)).getOrElse(Repository.generateUuid()),
      eventId,
      d.get("name").get,
      d.get("description").getOrElse(""),
      SessionImages(
        d.get("images.landing").getOrElse("")),
      SessionInfo(
        d.get("info.format").getOrElse(""),
        d.get("info.category").getOrElse(""),
        d.get("info.place").getOrElse(""),
        d.get("info.start").flatMap(d => parseDate(d)),
        d.get("info.end").flatMap(d => parseDate(d)),
        d.get("info.speakers").flatMap(json => if (json.isEmpty) None else Json.parse(json.replace("\r", "\\r").replace("\n", "\\n")).asOpt[List[Attendee]]).getOrElse(List()),
        d.get("info.slides"),
        d.get("info.video")),
      SessionMeta(
        d.get("meta.source.ref").map { ref => DataSource(ref, d.get("meta.source.name"), d.get("meta.source.url").getOrElse("")) },
        d.get("meta.created").flatMap(d => parseDate(d)).getOrElse(new DateTime()),
        d.get("meta.updated").flatMap(d => parseDate(d)).getOrElse(new DateTime()))))

  def toMap(e: Session): Map[String, String] = Map(
    "uuid" -> e.uuid,
    "eventId" -> e.eventId,
    "name" -> e.name,
    "description" -> e.description,
    "images.landing" -> e.images.landing,
    "info.format" -> e.info.format,
    "info.category" -> e.info.category,
    "info.place" -> e.info.place,
    "info.start" -> e.info.start.map(_.toString(FileImporter.dateFormat)).getOrElse(""),
    "info.end" -> e.info.end.map(_.toString(FileImporter.dateFormat)).getOrElse(""),
    "info.speakers" -> Json.stringify(Json.toJson(e.info.speakers)),
    "info.slides" -> e.info.slides.getOrElse(""),
    "info.video" -> e.info.video.getOrElse(""),
    "meta.source.ref" -> e.meta.source.map(_.ref).getOrElse(""),
    "meta.source.name" -> e.meta.source.flatMap(_.name).getOrElse(""),
    "meta.source.url" -> e.meta.source.map(_.url).getOrElse(""),
    "meta.created" -> e.meta.created.toString(FileImporter.dateFormat),
    "meta.updated" -> e.meta.updated.toString(FileImporter.dateFormat))

  def merge(e1: Session, e2: Session): Session = Session(
    e1.uuid,
    e1.eventId,
    merge(e1.name, e2.name),
    merge(e1.description, e2.description),
    merge(e1.images, e2.images),
    merge(e1.info, e2.info),
    merge(e1.meta, e2.meta))
  private def merge(e1: SessionImages, e2: SessionImages): SessionImages = SessionImages(
    merge(e1.landing, e2.landing))
  private def merge(e1: SessionInfo, e2: SessionInfo): SessionInfo = SessionInfo(
    merge(e1.format, e2.format),
    merge(e1.category, e2.category),
    merge(e1.place, e2.place),
    merge(e1.start, e2.start),
    merge(e1.end, e2.end),
    merge(e1.speakers, e2.speakers),
    merge(e1.slides, e2.slides),
    merge(e1.video, e2.video))
  private def merge(e1: SessionMeta, e2: SessionMeta): SessionMeta = SessionMeta(
    merge(e1.source, e2.source),
    e1.created,
    e2.updated)
  private def merge(e1: String, e2: String): String = if (e2.isEmpty) e1 else e2
  private def merge[A](e1: Option[A], e2: Option[A]): Option[A] = if (e2.isEmpty) e1 else e2
  private def merge[A](e1: List[A], e2: List[A]): List[A] = if (e2.isEmpty) e1 else e2
}

// mapping object for Session Form
case class SessionMetaData(
  source: Option[DataSource])
case class SessionData(
  eventId: String,
  name: String,
  description: String,
  images: SessionImages,
  info: SessionInfo,
  meta: SessionMetaData)
object SessionData {
  val fields = mapping(
    "eventId" -> nonEmptyText,
    "name" -> nonEmptyText,
    "description" -> text,
    "images" -> mapping(
      "landing" -> text)(SessionImages.apply)(SessionImages.unapply),
    "info" -> mapping(
      "format" -> text,
      "category" -> text,
      "place" -> text,
      "start" -> optional(jodaDate(pattern = "dd/MM/yyyy HH:mm")),
      "end" -> optional(jodaDate(pattern = "dd/MM/yyyy HH:mm")),
      "speakers" -> list(Attendee.fields),
      "slides" -> optional(text),
      "video" -> optional(text))(SessionInfo.apply)(SessionInfo.unapply),
    "meta" -> mapping(
      "source" -> optional(DataSource.fields))(SessionMetaData.apply)(SessionMetaData.unapply))(SessionData.apply)(SessionData.unapply)

  def toModel(d: SessionInfo): SessionInfo = SessionInfo(d.format, d.category, d.place, d.start, d.end, d.speakers.filter(!_.name.isEmpty), d.slides, d.video)
  def toModel(d: SessionMetaData): SessionMeta = SessionMeta(d.source, new DateTime(), new DateTime())
  def toModel(d: SessionData): Session = Session(Repository.generateUuid(), d.eventId, d.name, d.description, d.images, toModel(d.info), toModel(d.meta))
  def fromModel(d: SessionMeta): SessionMetaData = SessionMetaData(d.source)
  def fromModel(d: Session): SessionData = SessionData(d.eventId, d.name, d.description, d.images, d.info, fromModel(d.meta))
  def merge(m: SessionMeta, d: SessionMetaData): SessionMeta = toModel(d).copy(source = m.source, created = m.created)
  def merge(m: Session, d: SessionData): Session = toModel(d).copy(uuid = m.uuid, meta = merge(m.meta, d.meta))
}