/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
package io.megam.common.amqp.response

import scalaz._
import Scalaz._
import scalaz.effect.IO
import scalaz.EitherT._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._
import io.megam.common.jsonscalaz._
import java.nio.charset.Charset
import java.util.Date
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.convert.Wrappers.JConcurrentMapWrapper
import io.megam.common.amqp._
import io.megam.common.amqp.serialization.AMQPResponseSerialization

/**
 * @author ram
 *
 */
case class AMQPResponse(code: AMQPResponseCode,
  rawBody: RawBody,
  timeReceived: Date = new Date()) {
  import AMQPResponse._

  private lazy val rawBodyMap = new JConcurrentMapWrapper(new ConcurrentHashMap[Charset, String])
  private lazy val jValueMap = new JConcurrentMapWrapper(new ConcurrentHashMap[Charset, JValue])
  private lazy val jsonMap = new JConcurrentMapWrapper(new ConcurrentHashMap[(Charset, Boolean), String])

  def bodyString(implicit charset: Charset = UTF8Charset): String = {
    rawBodyMap.getOrElseUpdate(charset, new String(rawBody, charset))
  }

  def toJValue(implicit charset: Charset = UTF8Charset): JValue = {
    jValueMap.getOrElseUpdate(charset, toJSON(this)(AMQPResponseSerialization.writer))
  }

  def toJson(prettyPrint: Boolean = false)(implicit charset: Charset = UTF8Charset): String = {
    jsonMap.getOrElseUpdate((charset, prettyPrint), {
      if (prettyPrint) {
        prettyRender(toJValue)
      } else {
        compactRender(toJValue)
      }
    })
  }

}

object AMQPResponse {

  def fromJValue(jValue: JValue)(implicit charset: Charset = UTF8Charset): Result[AMQPResponse] = {
    fromJSON(jValue)(AMQPResponseSerialization.reader)
  }

  def fromJson(json: String): Result[AMQPResponse] = (Validation.fromTryCatchThrowable[JValue,Throwable] {
    parse(json)
  } leftMap { t: Throwable =>
    UncategorizedError(t.getClass.getCanonicalName, t.getMessage, List())
  }).toValidationNel.flatMap { j: JValue => fromJValue(j) }

  case class UnexpectedResponseCode(expected: AMQPResponseCode, actual: AMQPResponseCode)
    extends Exception("expected response code %d, got %d".format(expected.code, actual.code))

  case class JSONParsingError(errNel: NonEmptyList[Error]) extends Exception({
    errNel.map { err: Error =>
      err.fold(
        u => "unexpected JSON %s. expected %s".format(u.was.toString, u.expected.getCanonicalName),
        n => "no such field %s in json %s".format(n.name, n.json.toString),
        u => "uncategorized error %s while trying to decode JSON: %s".format(u.key, u.desc))
    }.list.mkString("\n")
  })
}
