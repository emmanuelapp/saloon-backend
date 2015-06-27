package controllers.api.compatibility

import models.event.Event
import models.event.Attendee
import models.event.Attendee._
import models.event.Session
import models.event.Exponent
import models.user.Device
import play.api.libs.json._

object Writer {
  val lastVersion: String = "v2"

  def write(data: Event, version: String): JsObject = version match {
    case "v1" => Json.obj(
      "uuid" -> data.uuid,
      "refreshUrl" -> data.meta.refreshUrl,
      "name" -> data.name,
      "description" -> data.description,
      "logoUrl" -> data.images.logo,
      "landingUrl" -> data.images.landing,
      "siteUrl" -> data.info.website,
      "start" -> data.info.start,
      "end" -> data.info.end,
      "address" -> data.info.address,
      "price" -> data.info.price.label,
      "priceUrl" -> data.info.price.url,
      "twitterHashtag" -> data.info.social.twitter.hashtag,
      "twitterAccount" -> data.info.social.twitter.account,
      "tags" -> data.meta.categories,
      "published" -> data.config.published,
      "created" -> data.meta.created,
      "updated" -> data.meta.updated,
      "className" -> Event.className)
    case "v2" => Json.toJson(data).as[JsObject] ++ Json.obj("className" -> Event.className)
    case _ => Json.obj("className" -> Event.className)
  }
  def write(data: Attendee, version: String): JsObject = version match {
    case "v1" => Json.obj(
      "name" -> data.name,
      "description" -> data.description,
      "company" -> data.info.company,
      "avatar" -> data.images.avatar,
      "profilUrl" -> data.info.website,
      "social" -> Json.toJson(data.social))
    case "v2" => Json.toJson(data).as[JsObject] ++ Json.obj("className" -> Attendee.className)
    case _ => Json.obj("className" -> Event.className)
  }
  def write(data: Session, speakers: List[Attendee], version: String): JsObject = version match {
    case "v1" => Json.obj(
      "uuid" -> data.uuid,
      "eventId" -> data.eventId,
      "name" -> data.name,
      "description" -> data.description,
      "format" -> data.info.format,
      "category" -> data.info.category,
      "place" -> data.info.place,
      "start" -> data.info.start,
      "end" -> data.info.end,
      "speakers" -> speakers.map(e => write(e, version)),
      "tags" -> Json.arr(),
      "created" -> data.meta.created,
      "updated" -> data.meta.updated,
      "className" -> Session.className)
    case "v2" => Json.toJson(data).as[JsObject] ++ Json.obj("className" -> Session.className)
    case _ => Json.obj("className" -> Event.className)
  }
  def write(data: Exponent, team: List[Attendee], version: String): JsObject = version match {
    case "v1" => Json.obj(
      "uuid" -> data.uuid,
      "eventId" -> data.eventId,
      "name" -> data.name,
      "description" -> data.description,
      "logoUrl" -> data.images.logo,
      "landingUrl" -> data.images.landing,
      "siteUrl" -> data.info.website,
      "team" -> team.map(e => write(e, version)),
      "level" -> data.info.level,
      "sponsor" -> data.info.sponsor,
      "tags" -> Json.arr(),
      "images" -> Json.arr(),
      "created" -> data.meta.created,
      "updated" -> data.meta.updated,
      "className" -> Exponent.className)
    case "v2" => Json.toJson(data).as[JsObject] ++ Json.obj("className" -> Exponent.className)
    case _ => Json.obj("className" -> Event.className)
  }

  def write(data: (Event, Int, Int, Int, Int), version: String): JsObject = write(data._1, version) ++ Json.obj(
    "attendeeCount" -> data._2,
    "sessionCount" -> data._3,
    "exponentCount" -> data._4,
    "actionCount" -> data._5)

  def write(event: Event, attendees: List[Attendee], sessions: List[Session], exponents: List[Exponent], version: String): JsObject = {
    write(event, version) ++ Json.obj(
      "attendees" -> attendees.map(e => write(e, version)),
      "sessions" -> sessions.map(e => write(e, attendees.filter(a => e.info.speakers.contains(a.uuid)), version)),
      "exponents" -> exponents.map(e => write(e, attendees.filter(a => e.info.team.contains(a.uuid)), version)))
  }

  //def write(data: Device): JsObject = Json.toJson(data).as[JsObject]
  def write(data: Device): JsObject = Json.obj(
    "uuid" -> data.uuid,
    "device" -> Json.obj(
      "uuid" -> data.info.uuid,
      "platform" -> data.info.platform,
      "manufacturer" -> data.info.manufacturer,
      "model" -> data.info.model,
      "version" -> data.info.version,
      "cordova" -> data.info.cordova),
    "saloonMemo" -> data.saloonMemo,
    "created" -> data.meta.created,
    "updated" -> data.meta.updated)
}
