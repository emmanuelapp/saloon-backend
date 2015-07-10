package backend.forms

import common.models.values.Address
import common.models.values.Link
import common.models.event.Event
import common.models.event.EventImages
import common.models.event.EventInfo
import common.models.event.EventInfoSocial
import common.models.event.EventInfoSocialTwitter
import common.models.event.EventEmail
import common.models.event.EventConfig
import common.models.event.EventMeta
import common.repositories.Repository
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.libs.json._

case class EventCreateData(
  name: String,
  categories: List[String],
  start: Option[DateTime],
  end: Option[DateTime],
  address: Address,
  website: String,
  price: Link,
  descriptionHTML: String,
  logo: String,
  landing: String,
  twitterHashtag: Option[String],
  twitterAccount: Option[String])
object EventCreateData {
  val fields = mapping(
    "name" -> nonEmptyText,
    "categories" -> list(text),
    "start" -> optional(jodaDate(pattern = "dd/MM/yyyy HH:mm")),
    "end" -> optional(jodaDate(pattern = "dd/MM/yyyy HH:mm")),
    "address" -> Address.fields,
    "website" -> text,
    "price" -> Link.fields,
    "descriptionHTML" -> text,
    "logo" -> text,
    "landing" -> text,
    "twitterHashtag" -> optional(text),
    "twitterAccount" -> optional(text))(EventCreateData.apply)(EventCreateData.unapply)

  def toMeta(d: EventCreateData): EventMeta = EventMeta(d.categories, None, None, new DateTime(), new DateTime())
  def toConfig(d: EventCreateData): EventConfig = EventConfig(None, false)
  def toSocial(d: EventCreateData): EventInfoSocial = EventInfoSocial(EventInfoSocialTwitter(d.twitterHashtag, d.twitterAccount))
  def toInfo(d: EventCreateData): EventInfo = EventInfo(d.website, d.start, d.end, d.address, d.price, toSocial(d))
  def toImages(d: EventCreateData): EventImages = EventImages(d.logo, d.landing)
  def toModel(d: EventCreateData): Event = Event(Repository.generateUuid(), d.name, d.descriptionHTML, toImages(d), toInfo(d), EventEmail(None), toConfig(d), toMeta(d))
  def fromModel(d: Event): EventCreateData = EventCreateData(d.name, d.meta.categories, d.info.start, d.info.end, d.info.address, d.info.website, d.info.price, d.description, d.images.logo, d.images.landing, d.info.social.twitter.hashtag, d.info.social.twitter.account)
  def merge(m: Event, d: EventCreateData): Event = m.copy(name = d.name, description = d.descriptionHTML, images = toImages(d), info = toInfo(d), meta = m.meta.copy(categories = d.categories, updated = new DateTime()))
}