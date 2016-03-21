package expander.akka

import akka.http.scaladsl.model.headers.{ Language, LanguageRange, `Accept-Language` }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpHeader, StatusCodes }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import play.api.libs.json._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import expander.core.{ ExpandContext, ResourceContext }

import scala.concurrent.{ ExecutionContext, Future }

class ExpanderFilterSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures with ScalatestRouteTest {

  val expandCtxProvider: collection.immutable.Seq[HttpHeader] ⇒ (Materializer, ExecutionContext) ⇒ ExpandContext[JsValue] = hrs ⇒ (_, _) ⇒ ExpandContext[JsValue] {
    root ⇒
      Map(__ \ "resolved" → ResourceContext[JsValue]{ params ⇒
        Future.successful(Json.obj(
          "status" → "ok",
          "params" → params.map(_.toString()),
          "headers" → JsObject(hrs.map(h ⇒ h.lowercaseName() → JsString(h.value())))
        ))
      })
  }

  val route = ExpanderFilter(ExpanderFilterConfig(expandCtxProvider, Set("accept-language"), conditionalEnabled = false)){
    path("filter") {
      complete(StatusCodes.OK → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.stringify(Json.obj("postId" → 1)))))
    }
  }

  "expander filter" should {
    "pass plain json" in {

      Get("/filter") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe Json.stringify(Json.obj("postId" → 1))
      }

    }

    "resolve" in {
      Get("/filter?_expand=resolved") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "resolved" \ "status").as[String] shouldBe "ok"
        (json \ "resolved" \ "headers").as[JsObject] shouldBe Json.obj()
      }

      Get("/filter?_expand=resolved").addHeader(`Accept-Language`(LanguageRange(Language("ru")))) ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "resolved" \ "status").as[String] shouldBe "ok"
        (json \ "resolved" \ "headers").as[JsObject] shouldBe Json.obj("accept-language" → "ru")
      }
    }
  }

}
