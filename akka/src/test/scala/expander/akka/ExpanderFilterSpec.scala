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

  val jsonGenericProvider = new JsonGenericProvider(Seq(
    ResolvePattern("post/:postId", __ \ "resolved", Set("postId"), Map("postId" → (__ \ "postId"))),
    ResolvePattern("images/:imageId", __ \ "image", Set("imageId"), Map("imageId" → (__ \ "imageId"))),
    ResolvePattern("users/:userId", __ \ "user", Set("userId"), Map("userId" → (__ \ "userId")))
  ))

  val expandCtxProvider: collection.immutable.Seq[HttpHeader] ⇒ (Materializer, ExecutionContext) ⇒ ExpandContext[JsValue] = hrs ⇒ (_, _) ⇒ ExpandContext[JsValue] {
    root ⇒
      jsonGenericProvider.apply(root).mapValues { url ⇒
        ResourceContext[JsValue]{ params ⇒
          Future.successful(Json.obj(
            "url" → url,
            "status" → "ok",
            "params" → params.toMap[String, String],
            "headers" → JsObject(hrs.map(h ⇒ h.lowercaseName() → JsString(h.value())))
          ))
        }
      }
  }

  val route = ExpanderFilter(ExpanderFilterConfig(expandCtxProvider, Set("accept-language"), conditionalEnabled = false)){
    path("post") {
      complete(StatusCodes.OK → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.stringify(Json.obj("postId" → 1)))))
    } ~ path("posts") {
      complete(StatusCodes.OK → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.stringify(Json.obj("items" → Seq(Json.obj("notToExpand" → true), Json.obj("postId" → 1, "imageId" → "i1"), Json.obj("postId" → 2, "imageId" → "i2"), Json.obj("imageId" → "i3")))))))
    } ~ path("awful") {
      complete(StatusCodes.OK → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(awfulJson)))
    }
  }

  "expander filter" should {
    "pass plain json" in {

      Get("/post") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe Json.stringify(Json.obj("postId" → 1))
      }

    }

    "resolve single" in {
      Get("/post?_expand=resolved") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "resolved" \ "status").as[String] shouldBe "ok"
        (json \ "resolved" \ "headers").as[JsObject] shouldBe Json.obj()
      }

      Get("/post?_expand=resolved").addHeader(`Accept-Language`(LanguageRange(Language("ru")))) ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "resolved" \ "status").as[String] shouldBe "ok"
        (json \ "resolved" \ "headers").as[JsObject] shouldBe Json.obj("accept-language" → "ru")
      }
    }

    "resolve list" in {
      Get("/posts?_expand=items*resolved") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "items" \\ "postId").map(_.as[Int]) shouldBe Seq(1, 2)
        (json \ "items" \\ "resolved").size shouldBe 2
      }
      Get("/posts?_expand=items*image") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "items" \\ "postId").map(_.as[Int]) shouldBe Seq(1, 2)
        (json \ "items" \\ "image").size shouldBe 3
      }

      Get("/posts?_expand=items*resolved(test:some)") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "items").as[JsArray].value.size shouldBe 3
        (json \ "items" \\ "postId").map(_.as[Int]) shouldBe Seq(1, 2)
        (json \ "items" \\ "resolved").size shouldBe 2
        (json \ "items" \\ "params").map(_.as[Map[String, String]]) shouldBe Seq(Map("test" → "some"), Map("test" → "some"))
      }
    }

    "resolve complex list" in {
      Get("/posts?_expand=items*resolved,items*image") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "items").as[JsArray].value.size shouldBe 4
        (json \ "items" \\ "postId").map(_.as[Int]) shouldBe Seq(1, 2)
        (json \ "items" \\ "resolved").size shouldBe 2
        (json \ "items" \\ "image").size shouldBe 3
      }
      Get("/posts?_expand=items{*resolved,*image}") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])
        (json \ "items").as[JsArray].value.size shouldBe 4
        (json \ "items" \\ "postId").map(_.as[Int]) shouldBe Seq(1, 2)
        (json \ "items").as[JsArray].head.get shouldBe Json.obj("notToExpand" → true)
        (json \ "items" \\ "resolved").size shouldBe 2
        (json \ "items" \\ "image").size shouldBe 3
      }
    }

    "resolve awful json" in {

      Get("/awful?_expand=items*user") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val json = Json.parse(responseAs[String])

        Json.prettyPrint(json)

        (json \ "items" \\ "user").length shouldBe 16
      }

    }
  }

}
