package expander.akka

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import play.api.libs.json._

class JsonGenericProviderSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val provider = new JsonGenericProvider(Seq(
    ResolvePattern("/things/:id", __ \ "thing", Map("id" → (__ \ "thingId")), Map("offset" → (__ \ "offset")), Set("offset")),
    ResolvePattern("/some/:complex/:path/to", __ \ "some", Map("complex" → (__ \ "complexId"), "path" → (__ \ "pathId")))
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

    }

    "resolve arrays of constants" in {

      provider(Json.obj("thingIds" → Seq(1, 2, 3))) shouldBe Map((__ \ "things" apply 0) → "/things/1", (__ \ "things" apply 1) → "/things/2", (__ \ "things" apply 2) → "/things/3")
      provider(Json.obj("pathId" → true, "complexIds" → Seq(1, 2, 3))) shouldBe Map((__ \ "somes" apply 0) → "/some/1/true/to", (__ \ "somes" apply 1) → "/some/2/true/to", (__ \ "somes" apply 2) → "/some/3/true/to")

    }
  }
}
