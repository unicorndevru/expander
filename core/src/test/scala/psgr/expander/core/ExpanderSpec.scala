package psgr.expander.core

import org.specs2._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.libs.json._

import scala.concurrent.Future

class ExpanderSpec extends mutable.Specification with FutureMatchers {

  implicit val ee = ExecutionEnv.fromGlobalExecutionContext

  val objMixin = Json.obj(
    "field" → "exists"
  )

  case class MapResolver(data: Map[String, JsObject]) extends JsonResolver {
    override def apply(v1: MetaRef, f: Field) = Future successful FieldResolveSuccess(v1, data(v1.href))
  }

  case class MixinResolver(mixin: JsObject) extends JsonResolver {
    override def apply(v1: MetaRef, f: Field) = Future.successful(FieldResolveSuccess(v1, Json.toJson(v1).asInstanceOf[JsObject] ++ mixin))
  }

  val mixinResolver = MixinResolver(objMixin)

  "expander" should {

    "get sub obj" in {
      val nestedJsonObj = Json.obj(
        "meta" → Json.obj("href" → "/parent"),
        "sub" → Json.obj(
          "meta" → Json.obj(
            "href" → "/parent/sub"
          )
        )
      )

      implicit val r = mixinResolver

      JsonExpander(nestedJsonObj, "sub").expand must beEqualTo(ResolveResult(Set(MetaRef("/parent/sub", path = Some("sub")), MetaRef("/parent", path = Some("$"))), Json.obj(
        "meta" → Json.obj("href" → "/parent", "path" → "$"),
        "sub" → Json.obj(
          "meta" → Json.obj(
            "href" → "/parent/sub",
            "path" → "sub"
          ),
          "field" → "exists"
        )
      ))).await
    }

    "get items" in {
      val itemsJson = Json.obj(
        "meta" → Json.obj("href" → "/list"),
        "items" → Seq(
          Json.obj("meta" → Json.obj("href" → "/list/0")),
          Json.obj("meta" → Json.obj("href" → "/list/1")),
          Json.obj("meta" → Json.obj("href" → "/list/2"))
        )
      )

      implicit val r = mixinResolver

      JsonExpander(itemsJson, "items").expand must beEqualTo(ResolveResult(
        Set(MetaRef("/list", path = Some("$")), MetaRef("/list/0", path = Some("items[0]")), MetaRef("/list/1", path = Some("items[1]")), MetaRef("/list/2", path = Some("items[2]"))),
        Json.obj(
          "meta" → Json.obj("href" → "/list", "path" → "$"),
          "items" → Seq(
            Json.obj(
              "meta" → Json.obj("href" → "/list/0", "path" → "items[0]"),
              "field" → "exists"
            ),
            Json.obj(
              "meta" → Json.obj("href" → "/list/1", "path" → "items[1]"),
              "field" → "exists"
            ),
            Json.obj(
              "meta" → Json.obj("href" → "/list/2", "path" → "items[2]"),
              "field" → "exists"
            )
          )
        )
      )).await
    }

    "get sub with point" in {
      val nestedJsonObj = Json.obj(
        "meta" → Json.obj("href" → "/parent"),
        "sub" → Json.obj(
          "meta" → Json.obj(
            "href" → "/parent/sub"
          ),
          "child" → Json.obj(
            "meta" → Json.obj(
              "href" → "/parent/sub/child"
            )
          )
        )
      )

      implicit val r = mixinResolver

      JsonExpander(nestedJsonObj, "sub.child").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/parent", path = Some("$")), MetaRef("/parent/sub", path = Some("sub")), MetaRef("/parent/sub/child", path = Some("sub.child"))),
          Json.obj(
            "meta" → Json.obj("href" → "/parent", "path" → "$"),
            "sub" → Json.obj(
              "meta" → Json.obj(
                "href" → "/parent/sub",
                "path" → "sub"
              ),
              "child" → Json.obj(
                "meta" → Json.obj(
                  "href" → "/parent/sub/child",
                  "path" → "sub.child"
                ),
                "field" → "exists"
              )
            )
          )
        )
      ).await
    }

    "get items with point" in {
      val itemsJson = Json.obj(
        "meta" → Json.obj("href" → "/list"),
        "sub" → Json.obj(
          "items" → Seq(
            Json.obj("meta" → Json.obj("href" → "/list/0")),
            Json.obj("meta" → Json.obj("href" → "/list/1")),
            Json.obj("meta" → Json.obj("href" → "/list/2"))
          )
        )
      )

      implicit val r = mixinResolver

      JsonExpander(itemsJson, "sub.items").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/list", path = Some("$")), MetaRef("/list/0", path = Some("sub.items[0]")), MetaRef("/list/1", path = Some("sub.items[1]")), MetaRef("/list/2", path = Some("sub.items[2]"))),
          Json.obj(
            "meta" → Json.obj("href" → "/list", "path" → "$"),
            "sub" → Json.obj("items" → Seq(
              Json.obj(
                "meta" → Json.obj("href" → "/list/0", "path" → "sub.items[0]"),
                "field" → "exists"
              ),
              Json.obj(
                "meta" → Json.obj("href" → "/list/1", "path" → "sub.items[1]"),
                "field" → "exists"
              ),
              Json.obj(
                "meta" → Json.obj("href" → "/list/2", "path" → "sub.items[2]"),
                "field" → "exists"
              )
            ))
          )
        )
      ).await
    }

    "get items sub with point" in {
      val itemsJson = Json.obj(
        "meta" → Json.obj("href" → "/list"),
        "sub" → Json.obj(
          "items" → Seq(
            Json.obj("child" →
              Json.obj("meta" → Json.obj("href" → "/list/0"))),
            Json.obj("child" →
              Json.obj("meta" → Json.obj("href" → "/list/1"))),
            Json.obj("child" →
              Json.obj("meta" → Json.obj("href" → "/list/2")))
          )
        )
      )

      implicit val r = mixinResolver

      JsonExpander(itemsJson, "sub.items.child").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/list", path = Some("$")), MetaRef("/list/0", path = Some("sub.items[0].child")), MetaRef("/list/1", path = Some("sub.items[1].child")), MetaRef("/list/2", path = Some("sub.items[2].child"))),
          Json.obj(
            "meta" → Json.obj("href" → "/list", "path" → "$"),
            "sub" → Json.obj("items" → Seq(
              Json.obj("child" →
                Json.obj(
                  "meta" → Json.obj("href" → "/list/0", "path" → "sub.items[0].child"),
                  "field" → "exists"
                )),
              Json.obj("child" →
                Json.obj(
                  "meta" → Json.obj("href" → "/list/1", "path" → "sub.items[1].child"),
                  "field" → "exists"
                )),
              Json.obj("child" →
                Json.obj(
                  "meta" → Json.obj("href" → "/list/2", "path" → "sub.items[2].child"),
                  "field" → "exists"
                ))
            ))
          )
        )
      ).await

    }

    "respect strict modifier" in {
      val obj = Json.obj("meta" → Json.obj("href" → "/root"), "sub" → Json.obj("f1" → "v1", "f2" → "v2"))

      implicit val r = mixinResolver

      JsonExpander(obj, "sub").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/root", path = Some("$"))), Json.obj("meta" → Json.obj("href" → "/root", "path" → "$"), "sub" → Json.obj("f1" → "v1", "f2" → "v2"))
        )
      ).await

      JsonExpander(obj, "sub.!f1").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/root", path = Some("$"))), Json.obj("meta" → Json.obj("href" → "/root", "path" → "$"), "sub" → Json.obj("f1" → "v1"))
        )
      ).await

      val obj1 = Json.obj("meta" → Json.obj("href" → "/root"), "sub" → Json.obj("meta" → Json.obj("href" → "/test")))

      JsonExpander(obj1, "sub.!field").expand must beEqualTo(
        ResolveResult(
          Set(MetaRef("/root", path = Some("$")), MetaRef("/test", path = Some("sub"))),
          Json.obj("meta" → Json.obj("href" → "/root", "path" → "$"), "sub" → Json.obj("field" → "exists", "meta" → Json.obj("href" → "/test", "path" → "sub")))
        )
      ).await

    }

    "perform complex loading" in {
      def m = (href: String) ⇒ Json.obj("meta" → Json.obj("href" → href))

      val a = m("/a") ++ Json.obj("sub" → m("/b"))

      val resolve = Map(
        "/b" → (m("/b") ++ Json.obj("child" → Json.obj("intern" → m("/c"), "keep" → "this", "other" → m("/f")))),
        "/c" → (m("/c") ++ Json.obj("items" → Seq(m("/d/0"), m("/d/1")))),
        "/d/0" → (m("/d/0") ++ Json.obj("field" → m("/e"))),
        "/d/1" → (m("/d/1") ++ Json.obj("ext-d1" → true)),
        "/e" → (m("/e") ++ Json.obj("ext-e" → true)),
        "/f" → (m("/f") ++ Json.obj("foo" → "bar"))
      )

      implicit val r = MapResolver(resolve)

      val expand = "sub.child.intern.items.field,sub.unexistent,sub.child.other"

      //JsonExpander.parse(expand.split(',').toList) must_== Map("sub" -> List("child.intern.items.field", "unexistent", "child.other"))

      JsonExpander(a, expand).expand.map(_.value.toString) must beEqualTo(
        """{"meta":{"href":"/a","path":"$"},"sub":{"meta":{"href":"/b","path":"sub"},"child":{"intern":{"meta":{"href":"/c","path":"sub.child.intern"},"items":[{"meta":{"href":"/d/0","path":"sub.child.intern.items[0]"},"field":{"meta":{"href":"/e","path":"sub.child.intern.items[0].field"},"ext-e":true}},{"meta":{"href":"/d/1","path":"sub.child.intern.items[1]"},"ext-d1":true}]},"keep":"this","other":{"meta":{"href":"/f","path":"sub.child.other"},"foo":"bar"}}}}"""
      ).await

    }
  }
}