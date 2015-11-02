package psgr.expander.protocol

import org.specs2.mutable.Specification
import play.api.libs.json.{ Writes, Json }

class MetaPatchSpec extends Specification {

  val id = MetaId[Res]("/res", 0)
  case class Res(items: Seq[Int], meta: MetaId[Res] = id) extends MetaResource[Res]

  implicit val w: Writes[Res] = Writes[Res]{ res ⇒
    Json.obj("meta" → res.meta, "items" → res.items)
  }

  "meta patch" should {
    "collect pulls" in {
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(1, 2))).isEmpty must beTrue
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(2))).$pull must beSome(Json.obj("items" → Seq(1)))
      MetaPatch[Res](Res(Seq(1, 2, 3)), Res(Seq(3))).$pull must beSome(Json.obj("items" → Seq(1, 2)))
    }
    "collect prepends" in {
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 1, 2))).$prepend must beSome(Json.obj("items" → Seq(0)))
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(-1, 0, 1, 2))).$prepend must beSome(Json.obj("items" → Seq(-1, 0)))
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(-1, 0, 1, 2, 3, 4, 5))).$prepend must beSome(Json.obj("items" → Seq(-1, 0)))
    }
    "collect appends" in {
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 1, 2, 3))).$append must beSome(Json.obj("items" → Seq(3)))
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(-1, 0, 1, 2, 3, 4, 5))).$append must beSome(Json.obj("items" → Seq(3, 4, 5)))
    }
    "pull, then prepend and append" in {
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 3))).$prepend must beNone
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 3))).$append must beNone
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 3))).$pull must beNone

      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 2, 3))).$append must beSome(Json.obj("items" → Seq(3)))
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 2, 3))).$prepend must beSome(Json.obj("items" → Seq(0)))
      MetaPatch[Res](Res(Seq(1, 2)), Res(Seq(0, 2, 3))).$pull must beSome(Json.obj("items" → Seq(1)))
    }

  }
}
