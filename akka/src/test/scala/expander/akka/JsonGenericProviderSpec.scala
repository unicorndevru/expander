package expander.akka

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import play.api.libs.json._

class JsonGenericProviderSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val provider = new JsonGenericProvider(Seq(
    ExpandPattern("/things/:id", __ \ "thing", Set("id"), Map("id" → (__ \ "thingId")), Map("offset" → (__ \ "offset"))),
    ExpandPattern("/some/:complex/:path/to", __ \ "some", Set("complex", "path"), Map("complex" → (__ \ "complexId"), "path" → (__ \ "pathId")))
  ))

  "provider" should {
    "resolve simple" in {

      provider(Json.obj()) shouldBe Map.empty
      provider(Json.obj("thingId" → "123")) shouldBe Map((__ \ "thing") → "/things/123")
      provider(Json.obj("thingId" → "123", "offset" → 1)) shouldBe Map((__ \ "thing") → "/things/123?offset=1")

    }

    "resolve complex path" in {

      provider(Json.obj()) shouldBe Map.empty
      provider(Json.obj("complexId" → 1)) shouldBe Map.empty
      provider(Json.obj("complexId" → 1, "pathId" → true)) shouldBe Map((__ \ "some") → "/some/1/true/to")

    }

    "resolve inside arrays" in {

      provider(Json.obj("test" → Json.obj("thingId" → "123", "offset" → 1))) shouldBe Map((__ \ "test" \ "thing") → "/things/123?offset=1")
      provider(Json.obj("test" → Seq(Json.obj("thingId" → "123", "offset" → 1)))) shouldBe Map(((__ \ "test" apply 0) \ "thing") → "/things/123?offset=1")
      provider(Json.obj("test" → Seq(Json.obj("thingId" → "123", "offset" → 1), Json.obj("thingId" → "345")))) shouldBe Map(((__ \ "test" apply 0) \ "thing") → "/things/123?offset=1", ((__ \ "test" apply 1) \ "thing") → "/things/345")

    }

    "resolve arrays of constants" in {

      provider(Json.obj("thingIds" → Seq(1, 2, 3))) shouldBe Map((__ \ "things" apply 0) → "/things/1", (__ \ "things" apply 1) → "/things/2", (__ \ "things" apply 2) → "/things/3")
      provider(Json.obj("pathId" → true, "complexIds" → Seq(1, 2, 3))) shouldBe Map((__ \ "somes" apply 0) → "/some/1/true/to", (__ \ "somes" apply 1) → "/some/2/true/to", (__ \ "somes" apply 2) → "/some/3/true/to")

    }

    "resolve very complex" in {

      val jsonGenericProvider = new JsonGenericProvider(Seq(
        ExpandPattern("images/:imageId", __ \ "image", Set("imageId"), Map("imageId" → (__ \ "imageId"))),
        ExpandPattern("users/:userId", __ \ "user", Set("userId"), Map("userId" → (__ \ "userId")))
      ))

      val res = jsonGenericProvider(Json.parse(awfulJson))

      res.get((__ \ "items" apply 0) \ "user") shouldBe defined
      res.get((__ \ "items" apply 1) \ "user") shouldBe defined
      res.get((__ \ "items" apply 2) \ "user") shouldBe defined
      res.get((__ \ "items" apply 3) \ "user") shouldBe defined
      res.get((__ \ "items" apply 4) \ "user") shouldBe defined
      res.get((__ \ "items" apply 5) \ "user") shouldBe defined
      res.get((__ \ "items" apply 6) \ "user") shouldBe defined
      res.get((__ \ "items" apply 7) \ "user") shouldBe defined
      res.get((__ \ "items" apply 8) \ "user") shouldBe defined
      res.get((__ \ "items" apply 9) \ "user") shouldBe defined
      res.get((__ \ "items" apply 10) \ "user") shouldBe defined
      res.get((__ \ "items" apply 11) \ "user") shouldBe defined
      res.get((__ \ "items" apply 12) \ "user") shouldBe defined
      res.get((__ \ "items" apply 13) \ "user") shouldBe defined
      res.get((__ \ "items" apply 14) \ "user") shouldBe defined
      res.get((__ \ "items" apply 15) \ "user") shouldBe defined

    }
  }
}
