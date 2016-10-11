/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
package io.megam.auth.funnel

import scalaz._
import Scalaz._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import jp.t2v.lab.play2.stackc.{ RequestWithAttributes, RequestAttributeKey, StackableController }

import io.megam.auth.funnel._
import io.megam.auth.funnel.FunnelErrors._
import io.megam.auth.stack.HeaderConstants._
import io.megam.auth.stack.GoofyCrypto._

/**
 * @author ram
 * A Request that comes to megam_play gets funnelled
 *            	 HTTPRequest
 *  		   (Headers, Body)
 *    				 |
 *   			    \ /
 *     				 |
 *   			 (becomes)
 *            FunneledRequest(maybeEmail,clientAPIHmac, clientAPIDate,
 *            clientAPIPath, clientAPIBody)
 */
case class FunneledRequest(maybeEmail: Option[String], maybeOrg: Option[String], clientAPIHmac: Option[String],
 clientAPIDate: Option[String], clientAPIPath: Option[String], clientAPIBody: Option[String], clientAPIPuttusavi: Option[String], clientMasterKey: Option[String]) {

  /**
   * We massage the email to check if it has a valid format
   */
  val wowEmail = {
    val EmailRegex = """^[a-z0-9_\+-]+(\.[a-z0-9_\+-]+)*@[a-z0-9-]+(\.[a-z0-9-]+)*\.([a-z]{2,4})$""".r
    maybeEmail.flatMap(x => EmailRegex.findFirstIn(x))
  } match {
    case Some(succ) => Validation.success[Throwable, Option[String]](succ.some)
    case None => Validation.failure[Throwable, Option[String]](new MalformedHeaderError(maybeEmail.get,
      """Email is blank or invalid. Must be in a standard format.\n"
      eg: goodemail@megam.io"""))
  }
  /**
   * Hmm this has created a dependency with GoofyCrypto. Not a good one.
   * This creates a signed string
   * concatenates (date + path + md5ed body) of the content sent via header
   */
  val mkSign = {
    val ab = ((clientAPIDate ++ clientAPIPath ++ toMD5(clientAPIBody)) map { a: String => a
    }).mkString("\n")
    ab
  }

  override def toString = {
    "%-16s%n [%-8s=%s]%n [%-8s=%s]%n [%-8s=%s]%n [%-8s=%s]%n [%-8s=%s]%n [%-8s=%s]%n [%-8s=%s]%n".format("FunneledRequest",
      "email", maybeEmail, "org", maybeOrg, "HMAC", clientAPIHmac, "DATE", clientAPIDate,
      "PATH", clientAPIPath, "BODY", clientAPIBody, "PUTTUSAVI", clientAPIPuttusavi, "MASTERKEY", clientMasterKey)
  }
}
case class FunnelRequestBuilder[A](req: RequestWithAttributes[A]) {

  private val rawheader = req.headers

  private val clientAPIReqBody = ((req.body).toString()).some
  private val clientAPIReqDate: Option[String] = rawheader.get(X_Megam_DATE)
  private val maybeOrg: Option[String] = rawheader.get(X_Megam_ORG)
  private val clientAPIPuttusavi: Option[String] = rawheader.get(X_Megam_PUTTUSAVI)
  private val clientMasterKey: Option[String] = rawheader.get(X_Megam_MASTERKEY)
  private val clientAPIReqPath: Option[String] = req.path.some
  //Look for the X_Megam_HMAC field. If not the FunneledRequest will be None.
  private lazy val frOpt: Option[FunneledRequest] = (for {
    hmac <- rawheader.get(X_Megam_HMAC)
    trimmed <- hmac.trim.some
    res <- trimmed.some
    if (res.indexOf(":") > 0)
  } yield {
    val res1 = res.split(":").take(2)
    FunneledRequest(res1(0).some, maybeOrg, res1(1).some, clientAPIReqDate, clientAPIReqPath, clientAPIReqBody, clientAPIPuttusavi, clientMasterKey)
  })

  /**
   *
   * Start with to see if the FunneledRequest exists, or else send a MalformedHeaderError back.
   * If the FunneledRequest exists, and an invalid email(format - not adhereing to our regex) is present lead to MalformedHeaderError.
   * A valid email exists, then send back a ValidationNel.success with FunneledRequest wrapped in Option.
   */
  def funneled: ValidationNel[Throwable, Option[FunneledRequest]] = {
    frOpt match {
      case Some(fr) => fr.wowEmail.leftMap { t: Throwable => t }.toValidationNel.flatMap {
        _: Option[String] => Validation.success[Error, Option[FunneledRequest]](fr.some).toValidationNel
      }
      case None => (Validation.failure[Throwable, Option[FunneledRequest]](
        new MalformedHeaderError("","""We couldn't parse the header. Didn't find %s. """.format(X_Megam_HMAC)))).toValidationNel
    }

  }

}
