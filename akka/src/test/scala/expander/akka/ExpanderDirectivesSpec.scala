package expander.akka

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import play.api.libs.json.{ JsObject, Json }
import testResources._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import akka.http.scaladsl.server.Directives._

class ExpanderDirectivesSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures with ScalatestRouteTest with ExpanderDirectives {

  val route = path("complex") {
    expand(Complex("aw", "wr"))
  }

  "expander directives" should {
    "pass plain json" in {

      Get("/complex") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe Json.stringify(Json.obj("awId" → "aw", "wrapId" → "wr"))
      }

    }

    "expand" in {

      Get("/complex?_expand=wrap") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe Json.stringify(Json.obj("awId" → "aw", "wrapId" → "wr", "wrap" → Json.obj("id" → "wr", "fooId" → "expFoo", "barId" → "expBar")))
      }

    }
  }

}
