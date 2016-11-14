package expander.akka

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import play.api.libs.json._

class ExpanderFilterConfigSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures with ScalatestRouteTest {

  "expander filter config" should {
    "read patterns" in {

      ExpanderFilterConfig.readPatterns(ConfigFactory.parseString(
        """
          |expander {
          |  patterns: [
          |     {
          |        url: "users/:userId"
          |        path: user
          |     }
          |     {
          |       url: "nodes/:nodeId"
          |       path: node
          |     }
          |  ]
          |}
        """.stripMargin
      )) shouldBe Seq(ExpandPattern(
        url = "users/:userId",
        path = __ \ "user",
        urlKeys = Set("userId"),
        required = Map("userId" → (__ \ "userId")),
        optional = Map.empty,
        applied = Map.empty
      ), ExpandPattern(
        url = "nodes/:nodeId",
        path = __ \ "node",
        urlKeys = Set("nodeId"),
        required = Map("nodeId" → (__ \ "nodeId")),
        optional = Map.empty,
        applied = Map.empty
      ))

      ExpanderFilterConfig.readPatterns(ConfigFactory.parseString(
        """
          |expander {
          |  patterns: [
          |     {
          |        url: "http://api:80/api/users/:id/res/:resId"
          |        path: res
          |        required {
          |           id: userId
          |        }
          |        optional {
          |           refresh: refresh
          |        }
          |     }
          |  ]
          |}
        """.stripMargin
      )) shouldBe Seq(ExpandPattern(
        url = "http://api:80/api/users/:id/res/:resId",
        path = __ \ "res",
        urlKeys = Set("id", "resId"),
        required = Map("id" → (__ \ "userId"), "resId" → (__ \ "resId")),
        optional = Map("refresh" → (__ \ "refresh")),
        applied = Map.empty
      ))

    }

    "read config" in {
      val efc = ExpanderFilterConfig.build(ConfigFactory.parseString(
        """
          |expander {
          |  set-headers {
          |     accept: application/json
          |  }
          |  enable-conditional: true
          |  forward-headers: [authorization, accept-language]
          |  base-url: "http://api:80/api/"
          |  patterns: [
          |     {
          |        url: "users/:userId"
          |        path: user
          |     }
          |  ]
          |}
        """.stripMargin
      ).withFallback(ConfigFactory.load()))

      efc.forwardHeaders shouldBe Set("authorization", "accept-language")
      efc.conditionalEnabled shouldBe true
    }

  }

}
