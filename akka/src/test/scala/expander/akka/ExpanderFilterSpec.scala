package expander.akka

import akka.http.scaladsl.model.{ HttpEntity, ContentTypes, StatusCodes }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import play.api.libs.json._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import akka.http.scaladsl.server.Directives._

class ExpanderFilterSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures with ScalatestRouteTest {

  val expandCtxProvider: JsValue ⇒ Map[JsPath, String] = json ⇒ {
    Seq((json \ "name").asOpt[String].map(name ⇒ (__ \ "joke") → s"http://api.icndb.com/?firstName=$name")).flatten.toMap
  }

  val route = ExpanderFilter(Seq("accept-language"), expandCtxProvider, system){
    path("filter") {
      complete(StatusCodes.OK → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.stringify(Json.obj("name" → "alessandro")))))
    }
  }

  "expander directives" should {
    "pass plain json" in {

      Get("/filter") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe Json.stringify(Json.obj("name" → "alessandro"))
      }

    }

    "filter" in {
      Get("/filter?_expand=joke") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "joke" \ "type").as[String] shouldBe "success"
        (json \ "joke" \ "value" \ "joke").as[String] should contain("alessandro")
      }
    }
  }

}
