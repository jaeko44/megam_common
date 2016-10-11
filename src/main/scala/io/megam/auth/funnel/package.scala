/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
package io.megam.auth

import scalaz._
import Scalaz._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._

import io.megam.auth.funnel.FunnelErrors._
import io.megam.json.funnel.FunnelResponsesSerialization

import jp.t2v.lab.play2.stackc.{ RequestWithAttributes, RequestAttributeKey, StackableController }
import java.io.{ StringWriter, PrintWriter }
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.nio.charset.Charset
import play.api.http.Status._

/**
 * @author ram
 *
 */
package object funnel {

  type FunneledHeader = Option[String]
  type FunneledBody = Option[String]
  type FunnelResponseList = NonEmptyList[FunnelResponse]
  type FunnelResponses = Option[FunnelResponseList]

  object FunnelResponses {

    //screwy. you pass an instance. may be FunnelResponses needs be to a case class
    def toJValue(fres: FunnelResponses): JValue = {
      import net.liftweb.json.scalaz.JsonScalaz.toJSON
      import FunnelResponsesSerialization.{ writer => FunResponsesWriter }
      toJSON(fres)(FunResponsesWriter)
    }

    //screwy. you pass an instance. may be FunnelResponses needs be to a case class
    def toJson(fres: FunnelResponses, prettyPrint: Boolean = false): String = if (prettyPrint) {
     prettyRender(toJValue(fres))
    } else {
      compactRender(toJValue(fres))
    }

    def toTuple2(fres: FunnelResponses): List[Tuple2[String,String]] = {
      (fres.flatMap { fres_list: FunnelResponseList =>
        (fres_list.map { fre => (fre.code.toString,fre.msg) }.list).some
      }).getOrElse(List(("000", "Done")))
    }

    def apply(f: FunnelResponse): FunnelResponses = FunnelResponses(nels(f))
    def apply(f: FunnelResponseList): FunnelResponses = f.some
    def apply(f: List[FunnelResponse]): FunnelResponses = f.toNel
    def empty: FunnelResponses = Option.empty[FunnelResponseList]
  }

  implicit def req2FunnelBuilder[A](req: RequestWithAttributes[A]): FunnelRequestBuilder[A] = new FunnelRequestBuilder[A](req)
  /**
   * Broadly erros are categorized into
   * 1. Authentication errors 2. Malformed Inputs 3. Service Errors 4. Resource Errors 5. JSON errors
   * JSON erros aren't traked yet
   */
  implicit class RichThrowable(thrownExp: Throwable) {
    def fold[T](
      cannotAuthError: CannotAuthenticateError => T,
      malformedBodyError: MalformedBodyError => T,
      malformedHeaderError: MalformedHeaderError => T,
      serviceUnavailableError: ServiceUnavailableError => T,
      resourceNotFound: ResourceItemNotFound => T,
      nopPerm: PermissionNotThere => T,
      anyError: Throwable => T): T = thrownExp match {
      case a @ CannotAuthenticateError(_, _, _) => cannotAuthError(a)
      case m @ MalformedBodyError(_, _, _)      => malformedBodyError(m)
      case h @ MalformedHeaderError(_, _, _)    => malformedHeaderError(h)
      case c @ ServiceUnavailableError(_, _, _) => serviceUnavailableError(c)
      case r @ ResourceItemNotFound(_, _, _)    => resourceNotFound(r)
      case f @ PermissionNotThere(_, _, _)     => nopPerm(f)
      case t @ _                                => anyError(t)
    }
  }

  implicit def err2FunnelResponse(hpret: HttpReturningError) = new FunnelResponse(hpret.code.getOrElse(BAD_REQUEST), hpret.msg, hpret.more.getOrElse(new String("none")), "Megam::Error", hpret.severity)

  implicit def err2FunnelResponses(hpret: HttpReturningError) = hpret.errNel.map { err: Throwable =>
    err.fold(a => new FunnelResponse(hpret.mkCode(a).getOrElse(BAD_REQUEST), hpret.mkMsg(a), hpret.mkMore(a), "Megam::Error", hpret.severity),
      m => new FunnelResponse(hpret.mkCode(m).getOrElse(BAD_REQUEST), hpret.mkMsg(m), hpret.mkMore(m), "Megam::Error", hpret.severity),
      h => new FunnelResponse(hpret.mkCode(h).getOrElse(BAD_REQUEST), hpret.mkMsg(h), hpret.mkMore(h), "Megam::Error", hpret.severity),
      c => new FunnelResponse(hpret.mkCode(c).getOrElse(BAD_REQUEST), hpret.mkMsg(c), hpret.mkMore(c), "Megam::Error", hpret.severity),
      r => new FunnelResponse(hpret.mkCode(r).getOrElse(BAD_REQUEST), hpret.mkMsg(r), hpret.mkMore(r), "Megam::Error", hpret.severity),
      f => new FunnelResponse(hpret.mkCode(f).getOrElse(BAD_REQUEST), hpret.mkMsg(f), hpret.mkMore(f), "Megam::Error", hpret.severity),
      t => new FunnelResponse(hpret.mkCode(t).getOrElse(BAD_REQUEST), hpret.mkMsg(t), hpret.mkMore(t), "Megam::Error", hpret.severity))
  }.some

  implicit def funnelResponses2Json(fres: FunnelResponses): String = FunnelResponses.toJson(fres, true)

}
