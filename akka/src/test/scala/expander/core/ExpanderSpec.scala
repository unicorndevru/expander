package expander.core

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, OptionValues, TryValues, WordSpec }

import testResources._

class ExpanderSpec extends WordSpec with Matchers with ScalaFutures with TryValues
    with OptionValues {

  import play.api.libs.json._

  "expander" should {

    "expand for 1 level" in {

      val root = Wrapper("wrapId", "fooId", "barId")

      expand(root, __ \ "sub" \ "foo", __ \ "sub" \ "bar").futureValue shouldBe Json.obj("id" → "wrapId", "fooId" → "fooId", "barId" → "barId", "sub" → Json.obj("foo" → Json.obj("id" → "fooId"), "bar" → Json.obj("id" → "barId")))
      expand(root, __ \ "sub").futureValue shouldBe Json.obj("id" → "wrapId", "fooId" → "fooId", "barId" → "barId", "sub" → Json.obj("foo" → Json.obj("id" → "fooId"), "bar" → Json.obj("id" → "barId")))
      expand(root, __ \ "sub" \ "foo").futureValue shouldBe Json.obj("id" → "wrapId", "fooId" → "fooId", "barId" → "barId", "sub" → Json.obj("foo" → Json.obj("id" → "fooId")))

    }

    "expand arrays" in {

      val root = ArrayWrapper("arrId", Seq("fooId", "barId"))

      expand(root).futureValue shouldBe Json.obj("id" → "arrId", "ids" → Seq("fooId", "barId"))
      expand(root, __ \ "arr").futureValue shouldBe Json.obj("id" → "arrId", "ids" → Seq("fooId", "barId"), "arr" → Seq(Json.obj("id" → "fooId"), Json.obj("id" → "barId")))
      expand(root, __ \ "arr" apply 0).futureValue shouldBe Json.obj("id" → "arrId", "ids" → Seq("fooId", "barId"), "arr" → Seq(Json.obj("id" → "fooId")))
      expand(root, __ \ "arr" apply 1).futureValue shouldBe Json.obj("id" → "arrId", "ids" → Seq("fooId", "barId"), "arr" → Seq(Json.obj("id" → "barId")))

    }

    "expand recursively" in {
      val root = Complex("awId", "wrapId")

      expand(root).futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId")
      expand(root, __ \ "wrap").futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId", "wrap" → Json.obj("id" → "wrapId", "fooId" → "expFoo", "barId" → "expBar"))
      expand(root, __ \ "wrap" \ "sub" \ "foo").futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId", "wrap" → Json.obj("id" → "wrapId", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("foo" → Json.obj("id" → "expFoo"))))
      expand(root, __ \ "wrap" \ "sub").futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId", "wrap" → Json.obj("id" → "wrapId", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("foo" → Json.obj("id" → "expFoo"), "bar" → Json.obj("id" → "expBar"))))
      expand(root, __ \ "arrw").futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId", "arrw" → Json.obj("id" → "awId", "ids" → Seq("id1", "id2")))
      expand(root, __ \ "arrw" \ "arr").futureValue shouldBe Json.obj("awId" → "awId", "wrapId" → "wrapId", "arrw" → Json.obj("id" → "awId", "ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1"), Json.obj("id" → "id2"))))

    }

    "respect RecursiveSearch" in {

      val root = Recursive(Seq("id1", "id2"))

      expand(root).futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"))
      expand(root, __ \ "arr").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar"), Json.obj("id" → "id2", "fooId" → "expFoo", "barId" → "expBar")))
      expand(root, (__ \ "arr" apply 0) \ "bar").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar")))
      expand(root, (__ \ "arr" apply 0) \ "sub" \ "bar").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar")))))
      expand(root, (__ \ "arr" apply 0) \ "sub" \ "bar", __ \ "arr" apply 1).futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar"))), Json.obj("id" → "id2", "fooId" → "expFoo", "barId" → "expBar")))
      expand(root, (__ \ "arr" apply 0) \ "sub" \ "bar", __ \ "arr").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar"))), Json.obj("id" → "id2", "fooId" → "expFoo", "barId" → "expBar")))
      expand(root, __ \ "arr" \\ "sub").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar"), "foo" → Json.obj("id" → "expFoo"))), Json.obj("id" → "id2", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar"), "foo" → Json.obj("id" → "expFoo")))))
      expand(root, __ \ "arr" \\ "sub" \ "bar").futureValue shouldBe Json.obj("ids" → Seq("id1", "id2"), "arr" → Seq(Json.obj("id" → "id1", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar"))), Json.obj("id" → "id2", "fooId" → "expFoo", "barId" → "expBar", "sub" → Json.obj("bar" → Json.obj("id" → "expBar")))))

    }
  }

}
