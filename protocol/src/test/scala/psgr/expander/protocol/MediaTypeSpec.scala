package psgr.expander.protocol

import org.specs2.mutable.Specification

class MediaTypeSpec extends Specification {
  case class Place(meta: Id)

  type Id = MetaId[Place]

  "media type reader" should {
    "parse place media type" in {
      MediaType.read("application/place+json;v=1") must beSome(MediaType(top = "application", subtype = "json", prod = Some("place"), params = Seq("v" → "1")))
      MediaType.json[Place].v(1).toString must_== MediaType(top = "application", subtype = "json", prod = Some("place"), params = Seq("v" → "1")).toString
    }
  }
}
