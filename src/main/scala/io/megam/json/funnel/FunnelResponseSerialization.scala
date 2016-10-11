/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
package io.megam.json.funnel

import scalaz._
import Scalaz._
import scalaz.effect.IO
import scalaz.EitherT._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.util.Date
import java.nio.charset.Charset
import io.megam.auth.funnel._
import io.megam.auth.funnel.FunnelErrors._
import io.megam.common.Constants._

/**
 * @author ram
 *
 */
class FunnelResponseSerialization(charset: Charset = UTF8Charset) extends io.megam.json.SerializationBase[FunnelResponse] {
  protected val JSONClazKey = JSON_CLAZ
  protected val CodeKey = "code"
  protected val MessageTypeKey = "msg_type"
  protected val MessageKey = "msg"
  protected val MoreKey = "more"
  protected val LinksKey ="links"

  override implicit val writer = new JSONW[FunnelResponse] {

    override def write(h: FunnelResponse): JValue = {
      JObject(
        JField(CodeKey, toJSON(h.code)) ::
          JField(MessageTypeKey, toJSON(h.msg_type)) ::
          JField (MessageKey, toJSON(h.msg)) ::
          JField(MoreKey, toJSON(h.more)) ::  JField(JSONClazKey, toJSON(h.json_claz)) :: JField(LinksKey, toJSON(h.links)) ::Nil)
    }
  }

  override implicit val reader = new JSONR[FunnelResponse] {

    override def read(json: JValue): Result[FunnelResponse] = {
      val codeField = field[Int](CodeKey)(json)
      val msgTypeField = field[String](MessageTypeKey)(json)
      val msgField = field[String](MessageKey)(json)
      val moreField = field[String](MoreKey)(json)
      val jsonClazField = field[String](JSONClazKey)(json)

      (codeField |@| msgTypeField |@| msgField |@| moreField |@| jsonClazField) {
        (code: Int, msg: String, more: String, msgType: String,jsonClazField: String) =>
          new FunnelResponse(code, msg, more,msgType,jsonClazField)
      }
    }
  }
}
