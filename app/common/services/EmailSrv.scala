package common.services

import common.Config
import common.models.values.typed._
import common.models.event.Event
import common.models.event.EventId
import common.models.user.User
import common.models.user.DeviceId
import common.models.user.Organization
import common.models.user.Request
import common.models.user.RequestId
import common.models.user.SubscribeUserAction
import common.repositories.event.EventRepository
import common.repositories.event.AttendeeRepository
import common.repositories.event.SessionRepository
import common.repositories.event.ExponentRepository
import common.repositories.user.UserActionRepository
import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.libs.concurrent.Execution.Implicits.defaultContext

case class EmailData(fromName: String, fromEmail: Email, to: Email, subject: String, html: TextHTML, text: TextMultiline)

object EmailSrv {
  def sendEmail(data: EmailData): Future[Boolean] = {
    SendGridSrv.sendEmail(data)
  }

  def generateEventReport(eventId: EventId, deviceId: DeviceId): Future[Option[EmailData]] = {
    UserActionRepository.findByUserEvent(deviceId, eventId).flatMap { actions =>
      val subscribeOpt = actions.find(_.action.isSubscribe())
      subscribeOpt.map {
        _.action match {
          case subscribe: SubscribeUserAction => {
            val favoriteSessionUuids = actions.filter(a => a.action.isFavorite() && a.itemType == ItemType.sessions).map(a => a.itemId.toSessionId)
            val favoriteExponentUuids = actions.filter(a => a.action.isFavorite() && a.itemType == ItemType.exponents).map(a => a.itemId.toExponentId)
            for {
              eventOpt <- EventRepository.getByUuid(eventId)
              attendees <- AttendeeRepository.findByEvent(eventId)
              sessions <- if (subscribe.filter == "favorites") SessionRepository.findByUuids(favoriteSessionUuids) else SessionRepository.findByEvent(eventId)
              exponents <- if (subscribe.filter == "favorites") ExponentRepository.findByUuids(favoriteExponentUuids) else ExponentRepository.findByEvent(eventId)
            } yield {
              val sessionsWithSpeakers = sessions.map(e => (e, attendees.filter(a => e.info.speakers.contains(a.uuid))))
              val exponentsWithTeam = exponents.map(e => (e, attendees.filter(a => e.info.team.contains(a.uuid))))
              val html = TextHTML(admin.views.html.Email.eventAttendeeReport(eventOpt.get, sessionsWithSpeakers, exponentsWithTeam, actions, subscribe.filter).toString)
              Some(EmailData(Config.Contact.name, Config.Contact.email, subscribe.email, s"Bilan ${eventOpt.get.name} by SalooN", html, html.toPlainText))
            }
          }
          case _ => Future(None) // not subscribed
        }
      }.getOrElse(Future(None)) // not subscribed
    }
  }

  def generateContactEmail(source: String, name: String, email: Email, message: String, userOpt: Option[User]): EmailData = {
    val html = TextHTML(common.views.html.Email.contact(source, name, email, message, userOpt).toString)
    val text = TextMultiline(common.views.txt.Email.contact(source, name, email, message, userOpt).toString)
    EmailData(name, email, Config.Contact.email, s"Contact SalooN depuis ${source}", html, text)
  }

  def generateAccountRequestEmail(email: Email, requestId: RequestId)(implicit req: RequestHeader): EmailData = {
    val saloonUrl = website.controllers.routes.Application.index().absoluteURL(Config.Application.secureUrl)
    val inviteUrl = authentication.controllers.routes.Auth.createAccount(requestId).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(authentication.views.html.Emails.accountRequest(email, saloonUrl, inviteUrl).toString)
    EmailData(Config.Contact.name, Config.Contact.email, email, "Invitation à SalooN", html, html.toPlainText)
  }

  def generatePasswordResetRequestEmail(email: Email, requestId: RequestId)(implicit req: RequestHeader): EmailData = {
    val inviteUrl = authentication.controllers.routes.Auth.passwordReset(requestId).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(authentication.views.html.Emails.passwordResetRequest(inviteUrl).toString)
    EmailData(Config.Contact.name, Config.Contact.email, email, "Réinitialisation du mot de passe SalooN", html, html.toPlainText)
  }

  def generateOrganizationRequestEmail(user: User, organization: Organization, organizationOwner: User, request: Request)(implicit req: RequestHeader): EmailData = {
    val acceptUrl = backend.controllers.routes.Requests.doAccept(request.uuid).absoluteURL(Config.Application.secureUrl)
    val rejectUrl = backend.controllers.routes.Requests.doReject(request.uuid).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(backend.views.html.Emails.organizationRequest(user, organization, request, acceptUrl, rejectUrl).toString)
    EmailData(user.name(), user.email, organizationOwner.email, s"Demande d'accès à l'organisation ${organization.name} sur SalooN", html, html.toPlainText)
  }

  def generateOrganizationRequestAcceptedEmail(user: User, organization: Organization, organizationOwner: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationRequestAccepted(organization, organizationOwner).toString)
    EmailData(Config.Contact.name, Config.Contact.email, user.email, s"Accès accordé à l'organisation ${organization.name} sur SalooN", html, html.toPlainText)
  }

  def generateOrganizationRequestRejectedEmail(user: User, organization: Organization): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationRequestRejected(organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, user.email, s"Accès à l'organisation ${organization.name} refusé :(", html, html.toPlainText)
  }

  def generateOrganizationInviteEmail(user: User, organization: Organization, invitedUser: User, request: Request)(implicit req: RequestHeader): EmailData = {
    val acceptUrl = backend.controllers.routes.Requests.doAccept(request.uuid).absoluteURL(Config.Application.secureUrl)
    val rejectUrl = backend.controllers.routes.Requests.doReject(request.uuid).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(backend.views.html.Emails.organizationInvite(organization, user, request, acceptUrl, rejectUrl).toString)
    EmailData(user.name(), user.email, invitedUser.email, s"Invitation à l'organisation ${organization.name} sur SalooN", html, html.toPlainText)
  }

  def generateOrganizationAndSalooNInviteEmail(user: User, organization: Organization, invitedEmail: Email, commentOpt: Option[TextMultiline], request: Request)(implicit req: RequestHeader): EmailData = {
    val inviteUrl = authentication.controllers.routes.Auth.createAccount(request.uuid).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(backend.views.html.Emails.organizationAndSalooNInvite(organization, user, commentOpt, request, inviteUrl).toString)
    EmailData(user.name(), user.email, invitedEmail, s"Invitation à l'organisation ${organization.name} sur SalooN", html, html.toPlainText)
  }

  def generateOrganizationInviteAcceptedEmail(invitedUser: User, organization: Organization, organizationOwner: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationInviteAccepted(invitedUser, organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, organizationOwner.email, s"Invitation à SalooN acceptée par ${invitedUser.name()}", html, html.toPlainText)
  }

  def generateOrganizationInviteRejectedEmail(invitedEmail: Email, organization: Organization, organizationOwner: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationInviteRejected(invitedEmail, organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, organizationOwner.email, s"Invitation à l'organisation ${organization.name} refusée :(", html, html.toPlainText)
  }

  def generateOrganizationInviteCanceledEmail(invitedEmail: Email, organization: Organization): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationInviteCanceled(organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, invitedEmail, s"Invitation à ${organization.name} annulée :(", html, html.toPlainText)
  }

  def generateOrganizationLeaveEmail(leavingUser: User, organization: Organization, organizationOwner: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationLeave(leavingUser, organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, organizationOwner.email, s"${leavingUser.name()} quitte l'organisation ${organization.name}", html, html.toPlainText)
  }

  def generateOrganizationBanEmail(bannedUser: User, organization: Organization, organizationOwner: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationBan(organization, organizationOwner).toString)
    EmailData(Config.Contact.name, Config.Contact.email, bannedUser.email, s"Vos accès à l'organisation ${organization.name} sont révoqués", html, html.toPlainText)
  }

  def generateOrganizationDeleteEmail(user: User, organization: Organization, deletingUser: User): EmailData = {
    val html = TextHTML(backend.views.html.Emails.organizationDelete(deletingUser, organization).toString)
    EmailData(Config.Contact.name, Config.Contact.email, user.email, s"${deletingUser.name()} supprime l'organisation ${organization.name}", html, html.toPlainText)
  }

  def generateEventPublishRequestEmail(user: User, event: Event)(implicit req: RequestHeader): EmailData = {
    val eventUrl = backend.controllers.routes.Events.details(event.uuid).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(backend.views.html.Emails.eventPublishRequest(user, event, eventUrl).toString)
    EmailData(user.name(), user.email, Config.Admin.email, s"Demande de publication de l'événement ${event.name}", html, html.toPlainText)
  }

  def generateEventPublishRequestCancelEmail(user: User, event: Event)(implicit req: RequestHeader): EmailData = {
    val html = TextHTML(backend.views.html.Emails.eventPublishRequestCancel(user, event).toString)
    EmailData(user.name(), user.email, Config.Admin.email, s"Demande de publication de l'événement ${event.name}", html, html.toPlainText)
  }

  def generateEventPublishedEmail(member: User, event: Event)(implicit req: RequestHeader): EmailData = {
    val eventUrl = backend.controllers.routes.Events.details(event.uuid).absoluteURL(Config.Application.secureUrl)
    val html = TextHTML(backend.views.html.Emails.eventPublished(event, eventUrl, Config.Application.googlePlayUrl, Config.Application.iTunesUrl).toString)
    EmailData(Config.Contact.name, Config.Contact.email, member.email, s"Publication de l'événement ${event.name}", html, html.toPlainText)
  }

}