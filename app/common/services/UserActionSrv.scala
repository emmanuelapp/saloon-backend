package common.services

import common.models.values.typed.GenericId
import common.models.values.typed.ItemType
import common.models.values.typed.TextMultiline
import common.models.event.EventItem
import common.models.event.Attendee
import common.models.event.Session
import common.models.event.Exponent
import common.models.user.Device
import common.models.user.UserAction
import common.models.user.UserActionContent
import common.models.user.FavoriteUserAction
import common.models.user.DoneUserAction
import common.models.user.MoodUserAction
import common.models.user.CommentUserAction
import common.models.user.SubscribeUserAction
import common.models.user.UserActionFull

object UserActionSrv {
  def byItem(actions: List[UserActionFull]): List[(EventItem, List[UserActionFull])] = {
    actions.groupBy(_.item).toList.sortBy(e => -e._2.length)
  }

  def byUser(actions: List[UserActionFull]): List[(Device, List[UserActionFull])] = {
    actions.groupBy(_.user).toList.sortBy(e => -e._2.length)
  }

  def byAction(actions: List[UserActionFull]): List[(UserActionContent, List[UserActionFull])] = {
    actions.groupBy(_.action).toList
  }

  def byActionName(actions: List[UserActionFull]): List[(String, List[UserActionFull])] = {
    actions.groupBy(a => toActionName(a.action)).toList
  }

  def byMood(actions: List[UserActionFull]): List[(String, List[UserActionFull])] = {
    actions.filter(_.action.isMood()).groupBy(_.action match {
      case MoodUserAction(rating, mood) => rating
      case _ => "unknown"
    }).toList
  }

  def toActionName(a: UserActionContent): String = {
    a match {
      case FavoriteUserAction(favorite) => FavoriteUserAction.className
      case DoneUserAction(done) => DoneUserAction.className
      case MoodUserAction(rating, mood) => MoodUserAction.className
      case CommentUserAction(text, comment) => CommentUserAction.className
      case SubscribeUserAction(email, filter, subscribe) => SubscribeUserAction.className
      case _ => "unknown"
    }
  }

  def favoriteSessions(actions: List[UserAction], sessions: List[(Session, List[Attendee])]): List[(Session, List[Attendee], Option[String], List[TextMultiline])] = {
    actions.filter(a => a.action.isFavorite() && a.itemType == ItemType.sessions).map { a =>
      sessions.find(_._1.uuid.unwrap == a.itemId.unwrap)
    }.flatten
      .filter(_._1.info.format != "break")
      .map { case (session, speakers) => (session, speakers, moodFor(actions, ItemType.sessions, session.uuid), commentsFor(actions, ItemType.sessions, session.uuid)) }
      .sortBy(r => -moodSort(r._3))
  }
  def notFavoriteSessions(actions: List[UserAction], sessions: List[(Session, List[Attendee])]): List[(Session, List[Attendee], Option[String], List[TextMultiline])] = {
    val favoriteUuids = actions.filter(a => a.action.isFavorite() && a.itemType == ItemType.sessions).map(_.itemId)
    sessions
      .filter(s => !favoriteUuids.contains(s._1.uuid) && s._1.info.format != "break")
      .map { case (session, speakers) => (session, speakers, moodFor(actions, ItemType.sessions, session.uuid), commentsFor(actions, ItemType.sessions, session.uuid)) }
      .sortBy(r => -moodSort(r._3))
  }
  def seenExponents(actions: List[UserAction], exponents: List[(Exponent, List[Attendee])]): List[(Exponent, List[Attendee], Option[String], List[TextMultiline])] = {
    actions.filter(a => a.action.isDone() && a.itemType == ItemType.exponents).map { a =>
      exponents.find(_._1.uuid.unwrap == a.itemId.unwrap)
    }.flatten
      .map { case (exponent, team) => (exponent, team, moodFor(actions, ItemType.exponents, exponent.uuid), commentsFor(actions, ItemType.exponents, exponent.uuid)) }
      .sortBy(r => -moodSort(r._3))
  }
  def notSeenExponents(actions: List[UserAction], exponents: List[(Exponent, List[Attendee])]): List[(Exponent, List[Attendee], Option[String], List[TextMultiline])] = {
    val seenUuids = actions.filter(a => a.action.isDone() && a.itemType == ItemType.exponents).map(_.itemId)
    exponents
      .filter(e => !seenUuids.contains(e._1.uuid))
      .map { case (exponent, team) => (exponent, team, moodFor(actions, ItemType.exponents, exponent.uuid), commentsFor(actions, ItemType.exponents, exponent.uuid)) }
      .sortBy(r => -moodSort(r._3))
  }
  def favoriteNotSeenExponents(actions: List[UserAction], exponents: List[(Exponent, List[Attendee])]): List[(Exponent, List[Attendee], Option[String], List[TextMultiline])] = {
    val seenUuids = actions.filter(a => a.action.isDone() && a.itemType == ItemType.exponents).map(_.itemId)
    actions.filter(a => a.action.isFavorite() && a.itemType == ItemType.exponents && !seenUuids.contains(a.itemId)).map { a =>
      exponents.find(_._1.uuid.unwrap == a.itemId.unwrap)
    }.flatten
      .map { case (exponent, team) => (exponent, team, moodFor(actions, ItemType.exponents, exponent.uuid), commentsFor(actions, ItemType.exponents, exponent.uuid)) }
      .sortBy(r => -moodSort(r._3))
  }
  def hasFavorites(actions: List[UserAction]): Boolean = actions.filter(_.action.isFavorite()).length > 0

  def listOpt[A](list: List[A]): Option[List[A]] = if (list.length > 0) Some(list) else None

  private def moodSort(m: Option[String]): Int = m.map {
    _ match {
      case "ok" => 3
      case "bof" => 2
      case "ko" => 1
      case _ => 0
    }
  }.getOrElse(0)
  private def moodFor(actions: List[UserAction], itemType: ItemType, itemId: GenericId): Option[String] = {
    actions.find(a => a.action.isMood() && a.itemType == itemType && a.itemId == itemId).flatMap {
      _.action match {
        case MoodUserAction(rating, mood) => Some(rating)
        case _ => None
      }
    }
  }
  private def commentsFor(actions: List[UserAction], itemType: ItemType, itemId: GenericId): List[TextMultiline] = {
    actions.filter(a => a.action.isComment() && a.itemType == itemType && a.itemId == itemId).map {
      _.action match {
        case CommentUserAction(text, comment) => Some(text)
        case _ => None
      }
    }.flatten
  }
}