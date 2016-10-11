/*
** Copyright [2013-2016] [Megam Systems]
**
** https://opensource.org/licenses/MIT
**
*/
/**
 * @author ram
 *
 */
import io.megam.common.amqp._
import io.megam.common.concurrent._
import io.megam.common.amqp.{ AMQPClient, RabbitMQClient }
import io.megam.common.amqp.response.{ AMQPResponse, AMQPResponseCode }

import org.specs2.Specification
import java.net.URL
import scalaz._
import Scalaz._
import scalaz.Validation._
import scala.concurrent.{ Future }
import scala.concurrent.duration.Duration

trait NSQClientTests { this: Specification =>

  class NSQClientTests {

    private val topic = "test"
    private val message1 = Messages("message" -> "{\"Id\":\"APR416511659171905536\"},{\"Action\":\"nstop\"},{\"Args\":\"Nah\"}")

    private def executeP(client: AMQPClient, expectedCode: AMQPResponseCode = AMQPResponseCode.Ok,
      duration: Duration = duration) = {
      import io.megam.common.concurrent.SequentialExecutionContext
      val messageJson = MessagePayLoad(message1).toJson(false)
      val responseFuture: Future[ValidationNel[Throwable, AMQPResponse]] =
        client.publish(messageJson).apply
      responseFuture.block(duration).toEither must beRight.like {
        case ampq_res => ampq_res.code must beEqualTo(expectedCode)
      }
    }

    def pubBADURL = {
      lazy val bad_uri = "localhost:4161"
      lazy val badClient = new NSQClient(bad_uri, "test")
      executeP(badClient)
    }

    def pubCONNDOWN = {
      lazy val badconn_uri = "http://localhost:4166"
      lazy val conndownClient = new NSQClient(badconn_uri, "test")
      executeP(conndownClient)
    }

    def pub = {
      lazy val good_uri = "http://localhost:4151"
      lazy val goodClient = new NSQClient(good_uri, "test")
      executeP(goodClient)
    }

  }

  object NSQClientTests {
    def apply = new NSQClientTests()
  }

}
