package backend.controllers

import common.models.values.typed.UserRole
import common.models.values.typed.TextMultiline
import common.models.user.User
import common.models.user.UserOrganization
import common.models.user.OrganizationData
import common.models.user.Request
import common.models.user.OrganizationRequest
import common.models.user.OrganizationInvite
import common.models.user.OrganizationId
import common.repositories.user.UserRepository
import common.repositories.user.OrganizationRepository
import common.repositories.user.RequestRepository
import common.repositories.event.EventRepository
import common.services.EventSrv
import common.services.EmailSrv
import backend.forms.UserData
import authentication.environments.SilhouetteEnvironment
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._

object Profile extends SilhouetteEnvironment {
  val userForm: Form[UserData] = Form(UserData.fields)
  val organizationForm: Form[OrganizationData] = Form(OrganizationData.fields)
  val accessRequestForm = Form(tuple("organizationId" -> of[OrganizationId], "comment" -> optional(of[TextMultiline])))

  def details = SecuredAction.async { implicit req =>
    implicit val user = req.identity
    for {
      organizations <- OrganizationRepository.findAll()
      pendingRequests <- RequestRepository.findPendingOrganizationRequestsByUser(user.uuid)
      pendingInvites <- RequestRepository.findPendingOrganizationInvitesByEmail(user.email)
      pendingRequestsForOwnedOrganizations <- RequestRepository.countPendingOrganizationRequestsFor(user.organizationIds.map(_.organizationId).filter(id => user.canAdministrateOrganization(id)))
    } yield {
      // split organizations
      val (memberOrganizations, notMemberOrganizations) = organizations.partition(o => user.organizationRole(o.uuid).isDefined)
      val (pendingOrganizations, otherOrganizations) = notMemberOrganizations.partition(o => findOrganizationRequest(pendingRequests ++ pendingInvites, o.uuid).isDefined)

      // transform collections
      val memberOrganizationsWithRole = memberOrganizations.map(o => (o, user.organizationRole(o.uuid).get, pendingRequestsForOwnedOrganizations.get(o.uuid).getOrElse(0))).sortBy {
        case (orga, role, pending) => role.getPriority
      }
      val pendingOrganizationsWithDate = pendingOrganizations.map(o => (o, findOrganizationRequest(pendingRequests ++ pendingInvites, o.uuid).get)).sortBy(_._2.created.getMillis())

      Ok(backend.views.html.Profile.details(memberOrganizationsWithRole, pendingOrganizationsWithDate, otherOrganizations, organizationForm, accessRequestForm))
    }
  }

  def update = SecuredAction { implicit req =>
    implicit val user = req.identity
    Ok(backend.views.html.Profile.update(userForm.fill(UserData.fromModel(user))))
  }

  def doUpdate = SecuredAction.async { implicit req =>
    implicit val user = req.identity
    userForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(backend.views.html.Profile.update(formWithErrors))),
      formData => UserRepository.update(user.uuid, UserData.merge(user, formData)).map {
        _.map { updatedElt =>
          Redirect(backend.controllers.routes.Profile.details).flashing("success" -> "Profil mis à jour avec succès")
        }.getOrElse {
          InternalServerError(backend.views.html.Profile.update(userForm.fill(formData))).flashing("error" -> "Erreur lors de la modification de votre profil")
        }
      })
  }

  def doCreateOrganization = SecuredAction.async { implicit req =>
    implicit val user = req.identity
    organizationForm.bindFromRequest.fold(
      formWithErrors => Future(Redirect(backend.controllers.routes.Profile.details()).flashing("error" -> "Votre organisation n'est pas correcte :(")),
      formData => createOrganization(formData, user).map {
        case (category, message, organizationIdOpt) =>
          organizationIdOpt.map { organizationId =>
            Redirect(backend.controllers.routes.Organizations.details(organizationId)).flashing(category -> message)
          }.getOrElse {
            Redirect(backend.controllers.routes.Profile.details()).flashing(category -> message)
          }
      })
  }

  def doOrganizationRequestAccess = SecuredAction.async { implicit req =>
    implicit val user = req.identity
    accessRequestForm.bindFromRequest.fold(
      formWithErrors => Future(Redirect(backend.controllers.routes.Profile.details()).flashing("error" -> "Impossible de demander l'accès à cette organisation :(")),
      formData => requestOrganisation(formData._1, formData._2, user).map {
        case (category, message) => Redirect(backend.controllers.routes.Profile.details()).flashing(category -> message)
      })
  }

  /*
   * Private methods
   */

  private def findOrganizationRequest(requests: List[Request], organizationId: OrganizationId): Option[Request] = {
    requests.find { r =>
      r.content match {
        case OrganizationRequest(id, _, _) => id == organizationId
        case OrganizationInvite(id, _, _, _) => id == organizationId
        case _ => false
      }
    }
  }

  private def createOrganization(formData: OrganizationData, user: User): Future[(String, String, Option[OrganizationId])] = {
    OrganizationRepository.getByName(formData.name).flatMap { orgOpt =>
      orgOpt.map { org =>
        Future(("error", s"L'organisation ${formData.name} existe déjà !", None))
      }.getOrElse {
        OrganizationRepository.insert(OrganizationData.toModel(formData)).flatMap { createdOpt =>
          createdOpt.map { created =>
            val userWithOrg = user.copy(organizationIds = user.organizationIds ++ List(UserOrganization(created.uuid, UserRole.owner)))
            UserRepository.update(userWithOrg.uuid, userWithOrg).map { userUpdatedOpt =>
              ("success", s"Votre organisation ${formData.name} vient d'être créée. Invitez d'autres personnes à vous rejoindre :)", Some(created.uuid))
            }
          }.getOrElse {
            Future(("error", s"Erreur lors de la création :(", None))
          }
        }
      }
    }
  }

  private def requestOrganisation(organizationId: OrganizationId, comment: Option[TextMultiline], user: User)(implicit req: RequestHeader): Future[(String, String)] = {
    val request = Request.organizationRequest(organizationId, comment, user)
    OrganizationRepository.getByUuid(organizationId).flatMap { orgOpt =>
      orgOpt.map { organization =>
        RequestRepository.insert(request).flatMap { err =>
          if (err.ok) {
            UserRepository.getOrganizationOwner(organization.uuid).flatMap {
              _.map { organizationOwner =>
                val emailData = EmailSrv.generateOrganizationRequestEmail(user, organization, organizationOwner, request)
                EmailSrv.sendEmail(emailData).map { success =>
                  if(success) ("success", s"Demande d'accès à ${organization.name} envoyée !") else ("error", "Problème d'envoi du mail")
                }
              }.getOrElse {
                Future(("error", s"Demande d'accès enregistrée mais le propriétaire de l'organisation n'a pas été trouvé :("))
              }
            }
          } else {
            Future(("error", "Échec de la demande d'accès"))
          }
        }
      }.getOrElse(Future(("error", "L'organisation demandée n'existe pas :(")))
    }
  }
}
