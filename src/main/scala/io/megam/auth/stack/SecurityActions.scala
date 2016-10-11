/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
package io.megam.auth.stack

import scalaz._
import Scalaz._
import scalaz.Validation._
import scalaz.effect.IO
import scalaz.EitherT._
import scalaz.NonEmptyList._

import jp.t2v.lab.play2.stackc.{ RequestWithAttributes, RequestAttributeKey, StackableController }

import io.megam.auth.funnel._
import io.megam.auth.funnel.FunnelErrors._
import io.megam.auth.stack._
import io.megam.auth.stack.GoofyCrypto._
import io.megam.auth.stack.SecurePasswordHashing._
import play.api.http.Status._

/**
 * @author rajthilak
 *
 */
case class AuthBag(email: String, org_id: String, api_key: String, authority: String)
case class AuthBagHMAC(bag: AuthBag, hmac: String, dbhmac: String)

object SecurityActions {

  def Authenticated[A](req: FunnelRequestBuilder[A],
    authImpl: String ⇒ ValidationNel[Throwable, Option[AccountResult]],
    masterImpl: String ⇒ ValidationNel[Throwable, Option[MasterKeyResult]]): ValidationNel[Throwable, Option[AuthBag]] = {
    req.funneled match {
      case Success(succ) ⇒ {
        (succ map (x ⇒ bazookaAtDataSource(x, authImpl, masterImpl))).getOrElse(
          Validation.failure[Throwable, Option[AuthBag]](CannotAuthenticateError("Invalid header.",
            "Mismatch.")).toValidationNel)

      }
      case Failure(err) ⇒
        val errm = (err.list.map(m ⇒ m.getMessage)).mkString("\n")
        Validation.failure[Error, Option[AuthBag]](CannotAuthenticateError(
          "Invalid header.", errm)).toValidationNel
    }
  }

  def Validate(password: String, password_hash: String): ValidationNel[Throwable, Option[Boolean]] = {
    if (SecurePasswordHashing.validatePassword(password, password_hash)) {
      Validation.success[Throwable, Option[Boolean]](true.some).toValidationNel
    } else {
      Validation.failure[Throwable, Option[Boolean]](CannotAuthenticateError(
        "Login failed.", "Mismatch password hash")).toValidationNel
    }
  }

  /**
   *
   * This Authenticated function will extract information from the request and calculate
   * an HMAC value. The request is parsed as tolerant text, as content type is application/json,
   * which isn't picked up by the default body parsers in the controller.
   * If the header exists then
   * the string is split on : and the header is parsed
   * else
   */
  def bazookaAtDataSource(funldRequest: FunneledRequest,
    authImpl: String ⇒ ValidationNel[Throwable, Option[AccountResult]],
    masterImpl: String ⇒ ValidationNel[Throwable, Option[MasterKeyResult]]): ValidationNel[Throwable, Option[AuthBag]] = {
    (for {
      dbMasterRespOpt ← eitherT[IO, NonEmptyList[Throwable], Option[MasterKeyResult]] {
        (masterImpl("1").disjunction).pure[IO]
      }
      dbRespOpt ← eitherT[IO, NonEmptyList[Throwable], Option[AccountResult]] {
        (authImpl(funldRequest.maybeEmail.get).disjunction).pure[IO]
      }
      found ← eitherT[IO, NonEmptyList[Throwable], Option[AuthBag]] {
        val dbResp = dbRespOpt.get
        val dbMasterResp = dbMasterRespOpt.get
        funldRequest.clientMasterKey match {
          case Some(p) ⇒ {
            if (dbMasterResp != null && dbResp != null) {
              val a = AuthBagHMAC(AuthBag(dbResp.email, funldRequest.maybeOrg.get, dbMasterResp.key,Role.ADMIN),
                funldRequest.clientAPIHmac.get, toHMAC(dbMasterResp.key, funldRequest.mkSign))
              GoofyCrypto.compareFor(a, "email/masterkey")
            } else {
              (nels((CannotAuthenticateError("""Authorization failure: ✘ '%s'."""
                .format(dbResp.email).stripMargin, "", UNAUTHORIZED))): NonEmptyList[Throwable]).left[Option[AuthBag]].pure[IO]
            }
          }
          case None ⇒ {
            if (dbResp != null) {
              funldRequest.clientAPIPuttusavi match {
                case Some(p) ⇒ {
                  val a = AuthBagHMAC(AuthBag(dbResp.email, funldRequest.maybeOrg.get, dbResp.password.password_hash, dbResp.states.authority),
                    funldRequest.clientAPIHmac.get, toHMAC(dbResp.password.password_hash, funldRequest.mkSign))
                  GoofyCrypto.compareFor(a, "email/password")
                }
                case None ⇒ {
                  val a = AuthBagHMAC(AuthBag(dbResp.email, funldRequest.maybeOrg.get, dbResp.api_key, dbResp.states.authority),
                    funldRequest.clientAPIHmac.get, toHMAC(dbResp.api_key, funldRequest.mkSign))
                  GoofyCrypto.compareFor(a, "email/api-key") //this has to be a trait.
                }
              }
            } else {
              (nels((CannotAuthenticateError("""Authorization failure: ✘ '%s'."""
                .format(dbResp.email).stripMargin, "", UNAUTHORIZED))): NonEmptyList[Throwable]).left[Option[AuthBag]].pure[IO]
            }
          }
        }
      }
    } yield found).run.map(_.validation).unsafePerformIO()
  }
}
