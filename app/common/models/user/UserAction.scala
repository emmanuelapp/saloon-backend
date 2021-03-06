package common.models.user

import common.models.utils.tString
import common.models.utils.tStringHelper
import common.models.values.UUID
import common.models.values.typed._
import common.models.event.Event
import common.models.event.EventId
import common.models.event.EventItem
import org.joda.time.DateTime
import play.api.libs.json._
import common.models.values.typed.TextMultiline

case class UserActionId(id: String) extends AnyVal with tString with UUID {
  def unwrap: String = this.id
}
object UserActionId extends tStringHelper[UserActionId] {
  def generate(): UserActionId = UserActionId(UUID.generate())
  def build(str: String): Either[String, UserActionId] = UUID.toUUID(str).right.map(id => UserActionId(id)).left.map(_ + " for UserActionId")
}

case class UserAction(
  uuid: UserActionId,
  userId: DeviceId, // id of Device (TODO: should add type 'user' or 'device')
  action: UserActionContent,
  itemType: ItemType,
  itemId: GenericId,
  eventId: Option[EventId],
  created: DateTime,
  updated: DateTime) {
  def withContent(c: UserActionContent, time: Option[DateTime] = None): UserAction = this.copy(action = c, updated = time.getOrElse(new DateTime()))
  def toMap(): Map[String, String] = {
    Map(
      "eventId" -> this.eventId.map(_.unwrap).getOrElse(""),
      "userId" -> this.userId.unwrap,
      "itemType" -> this.itemType.unwrap,
      "itemId" -> this.itemId.unwrap) ++ this.action.toMap()
  }
}
object UserAction {
  def favorite(userId: DeviceId, itemType: ItemType, itemId: GenericId, eventId: EventId, time: Option[DateTime] = None): UserAction = UserAction(UserActionId.generate(), userId, FavoriteUserAction(), itemType, itemId, Some(eventId), time.getOrElse(new DateTime()), time.getOrElse(new DateTime()))
  def done(userId: DeviceId, itemType: ItemType, itemId: GenericId, eventId: EventId, time: Option[DateTime] = None): UserAction = UserAction(UserActionId.generate(), userId, DoneUserAction(), itemType, itemId, Some(eventId), time.getOrElse(new DateTime()), time.getOrElse(new DateTime()))
  def mood(userId: DeviceId, itemType: ItemType, itemId: GenericId, rating: String, eventId: EventId, time: Option[DateTime] = None): UserAction = UserAction(UserActionId.generate(), userId, MoodUserAction(rating), itemType, itemId, Some(eventId), time.getOrElse(new DateTime()), time.getOrElse(new DateTime()))
  def comment(userId: DeviceId, itemType: ItemType, itemId: GenericId, text: TextMultiline, eventId: EventId, time: Option[DateTime] = None): UserAction = UserAction(UserActionId.generate(), userId, CommentUserAction(text), itemType, itemId, Some(eventId), time.getOrElse(new DateTime()), time.getOrElse(new DateTime()))
  def subscribe(userId: DeviceId, itemType: ItemType, itemId: GenericId, email: Email, filter: String, eventId: EventId, time: Option[DateTime] = None): UserAction = UserAction(UserActionId.generate(), userId, SubscribeUserAction(email, filter), itemType, itemId, Some(eventId), time.getOrElse(new DateTime()), time.getOrElse(new DateTime()))

  private implicit val formatFavoriteUserAction = Json.format[FavoriteUserAction]
  private implicit val formatDoneUserAction = Json.format[DoneUserAction]
  private implicit val formatMoodUserAction = Json.format[MoodUserAction]
  private implicit val formatCommentUserAction = Json.format[CommentUserAction]
  private implicit val formatSubscribeUserAction = Json.format[SubscribeUserAction]
  private implicit val formatUserActionContent = Format(
    __.read[FavoriteUserAction].map(x => x: UserActionContent)
      .orElse(__.read[DoneUserAction].map(x => x: UserActionContent))
      .orElse(__.read[MoodUserAction].map(x => x: UserActionContent))
      .orElse(__.read[CommentUserAction].map(x => x: UserActionContent))
      .orElse(__.read[SubscribeUserAction].map(x => x: UserActionContent)),
    Writes[UserActionContent] {
      case favorite: FavoriteUserAction => Json.toJson(favorite)(formatFavoriteUserAction)
      case done: DoneUserAction => Json.toJson(done)(formatDoneUserAction)
      case mood: MoodUserAction => Json.toJson(mood)(formatMoodUserAction)
      case comment: CommentUserAction => Json.toJson(comment)(formatCommentUserAction)
      case subscribe: SubscribeUserAction => Json.toJson(subscribe)(formatSubscribeUserAction)
    })
  implicit val format = Json.format[UserAction]
}

case class UserActionFull(event: Event, user: Device, action: UserActionContent, item: EventItem) {
  def toMap(): Map[String, String] = {
    Map(
      "eventId" -> this.event.uuid.unwrap,
      "eventName" -> this.event.name.unwrap,
      "userId" -> this.user.uuid.unwrap,
      "itemType" -> this.item.getType().unwrap,
      "itemId" -> this.item.uuid.unwrap,
      "itemName" -> this.item.name.unwrap) ++ this.action.toMap()
  }
}

sealed trait UserActionContent {
  def toMap(): Map[String, String] = this match {
    case FavoriteUserAction(favorite) => Map("actionType" -> FavoriteUserAction.className, "rating" -> "", "text" -> "", "email" -> "", "filter" -> "")
    case DoneUserAction(done) => Map("actionType" -> DoneUserAction.className, "rating" -> "", "text" -> "", "email" -> "", "filter" -> "")
    case MoodUserAction(rating, mood) => Map("actionType" -> MoodUserAction.className, "rating" -> rating, "text" -> "", "email" -> "", "filter" -> "")
    case CommentUserAction(text, comment) => Map("actionType" -> CommentUserAction.className, "rating" -> "", "text" -> text.unwrap, "email" -> "", "filter" -> "")
    case SubscribeUserAction(email, filter, subscribe) => Map("actionType" -> SubscribeUserAction.className, "rating" -> "", "text" -> "", "email" -> email.unwrap, "filter" -> filter)
    case _ => Map("actionType" -> "Unknown", "rating" -> "", "text" -> "", "email" -> "", "filter" -> "")
  }

  def isFavorite(): Boolean = isType(FavoriteUserAction.className)
  def isDone(): Boolean = isType(DoneUserAction.className)
  def isMood(): Boolean = isType(MoodUserAction.className)
  def isComment(): Boolean = isType(CommentUserAction.className)
  def isSubscribe(): Boolean = isType(SubscribeUserAction.className)
  def isType(actionType: String): Boolean = this match {
    case _: FavoriteUserAction => actionType == FavoriteUserAction.className
    case _: DoneUserAction => actionType == DoneUserAction.className
    case _: MoodUserAction => actionType == MoodUserAction.className
    case _: CommentUserAction => actionType == CommentUserAction.className
    case _: SubscribeUserAction => actionType == SubscribeUserAction.className
    case _ => false
  }
}
case class FavoriteUserAction(favorite: Boolean = true) extends UserActionContent
object FavoriteUserAction {
  val className = "favorite"
}
case class DoneUserAction(done: Boolean = true) extends UserActionContent
object DoneUserAction {
  val className = "done"
}
case class MoodUserAction(rating: String, mood: Boolean = true) extends UserActionContent // TODO => feedback
object MoodUserAction {
  val className = "mood"
}
case class CommentUserAction(text: TextMultiline, comment: Boolean = true) extends UserActionContent // TODO => personal notes (memo)
object CommentUserAction {
  val className = "comment"
}
// TODO add public comment
case class SubscribeUserAction(email: Email, filter: String, subscribe: Boolean = true) extends UserActionContent
object SubscribeUserAction {
  val className = "subscribe"
}
