package conferences.models

import common.Defaults
import common.models.utils._
import common.models.values.UUID
import common.services.TwitterCard
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.libs.json.Json

case class PresentationId(id: String) extends AnyVal with tString with UUID {
  def unwrap: String = this.id
}
object PresentationId extends tStringHelper[PresentationId] {
  def generate(): PresentationId = PresentationId(UUID.generate())
  def build(str: String): Either[String, PresentationId] = UUID.toUUID(str).right.map(id => PresentationId(id)).left.map(_ + " for PresentationId")
}

case class Presentation(
  conferenceId: ConferenceId,
  id: PresentationId,
  title: String,
  description: Option[String],
  slidesUrl: Option[String],
  videoUrl: Option[String],
  speakers: List[PresentationSpeaker],
  start: Option[DateTime],
  end: Option[DateTime],
  room: Option[String],
  tags: List[String],
  created: DateTime,
  createdBy: Option[User]) {
  def toTwitterCard(): TwitterCard = TwitterCard( // TODO : improve presentation twitter card
    "summary",
    "@conferencelist_",
    title,
    description.getOrElse(""),
    "https://avatars2.githubusercontent.com/u/11368266?v=3&s=200")
  def toTwitt(): String = "" // TODO : prefilled text to twitt about this presentation
}
case class PresentationSpeaker(
  name: String,
  email: Option[String],
  siteUrl: Option[String],
  twitter: Option[String]) {
  def trim(): PresentationSpeaker = this.copy(
    name = this.name.trim,
    email = this.email.map(_.trim),
    twitter = this.twitter.map(_.trim.replace("@", "").replaceAll("https?://twitter.com/", ""))
  )
}
object PresentationSpeaker {
  implicit val formatPresentationSpeaker = Json.format[PresentationSpeaker]
}
object Presentation {
  implicit val formatPresentation = Json.format[Presentation]
}

case class PresentationData(
  conferenceId: ConferenceId,
  id: Option[PresentationId],
  title: String,
  description: Option[String],
  slidesUrl: Option[String],
  videoUrl: Option[String],
  speakers: List[PresentationSpeaker],
  start: Option[DateTime],
  duration: Option[Int],
  room: Option[String],
  tags: List[String],
  createdBy: User)
object PresentationData {
  val fields = mapping(
    "conferenceId" -> of[ConferenceId],
    "id" -> optional(of[PresentationId]),
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "slidesUrl" -> optional(nonEmptyText),
    "videoUrl" -> optional(nonEmptyText),
    "speakers" -> list(mapping(
      "name" -> nonEmptyText,
      "email" -> optional(nonEmptyText),
      "siteUrl" -> optional(nonEmptyText),
      "twitter" -> optional(nonEmptyText)
    )(PresentationSpeaker.apply)(PresentationSpeaker.unapply)),
    "start" -> optional(jodaDate(pattern = Defaults.datetimeFormat)),
    "duration" -> optional(number),
    "room" -> optional(nonEmptyText),
    "tags" -> list(nonEmptyText),
    "createdBy" -> User.fields
  )(PresentationData.apply)(PresentationData.unapply)
  def toModel(d: PresentationData): Presentation = Presentation(
    d.conferenceId,
    d.id.getOrElse(PresentationId.generate()),
    d.title,
    d.description,
    d.slidesUrl,
    d.videoUrl,
    d.speakers,
    d.start,
    d.start.flatMap(s => d.duration.map(duration => s.plusMinutes(duration))),
    d.room,
    d.tags,
    new DateTime(),
    Some(d.createdBy.trim()))
  def fromModel(m: Presentation): PresentationData = PresentationData(
    m.conferenceId,
    Some(m.id),
    m.title,
    m.description,
    m.slidesUrl,
    m.videoUrl,
    m.speakers,
    m.start,
    m.end.flatMap(e => m.start.map(s => ((e.getMillis - s.getMillis) / (1000*60)).toInt)),
    m.room,
    m.tags,
    User.empty)
}