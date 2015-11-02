package psgr.expander.core

import org.specs2.mutable

class FieldsSpec extends mutable.Specification {
  "fields parser" should {
    "parse simple field" in {
      Field.read("name") must_== Field("$", Map("name" → Field("name")))
    }
    "parse enumeration of simple fields" in {
      Field.read("name,other") must_== Field("$", Map("name" → Field("name"), "other" → Field("other")))
      Field.read("name,name,other,other") must_== Field("$", Map("name" → Field("name"), "other" → Field("other")))
      Field.read("name,name") must_== Field("$", Map("name" → Field("name")))
    }
    "parse field with a dot" in {
      Field.read("name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")))))
      Field.read("name,name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")))))
      Field.read("name,name.sub,name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")))))
      Field.read("name,name.sub,name.sub,other") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"))), "other" → Field("other")))
      Field.read("name,name.sub,name.sub,other.sub2,other") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"))), "other" → Field("other", Map("sub2" → Field("sub2")))))
    }
    "parse field with params" in {
      Field.read("name()") must_== Field("$", Map("name" → Field("name")))
      Field.read("name(limit:1)") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "1"))))
      Field.read("name(limit:1,offset:2)") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "1", "offset" → "2"))))
      Field.read("name(limit:1,offset:2),other") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "1", "offset" → "2")), "other" → Field("other")))
      Field.read("name(limit:1,offset:2),other(param:value)") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "1", "offset" → "2")), "other" → Field("other", query = Map("param" → "value"))))
      Field.read("name(limit:1,offset:2).sub,head.other(param:value)") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), query = Map("limit" → "1", "offset" → "2")), "head" → Field("head", Map("other" → Field("other", query = Map("param" → "value"))))))
      Field.read("name(limit:$-1%)") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "$-1%"))))
      Field.read("name(limit:$+1%)") must_== Field("$", Map("name" → Field("name", query = Map("limit" → "$+1%"))))
    }
    "parse field with {subfields}" in {
      Field.read("name{}") must_== Field("$", Map("name" → Field("name")))
      Field.read("name.{}") must_== Field("$", Map("name" → Field("name")))
      Field.read("name.{sub}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")))))
      Field.read("name.{sub,foo,bar}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"), "foo" → Field("foo"), "bar" → Field("bar")))))
      Field.read("name.{sub,foo.bar,baz.{b1,b2}}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"), "foo" → Field("foo", Map("bar" → Field("bar"))), "baz" → Field("baz", Map("b1" → Field("b1"), "b2" → Field("b2")))))))
      Field.read("name.{sub(p:v),foo(pf:vf).bar(pr:vr),baz.{b1(p1:v1),b2}}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub", query = Map("p" → "v")), "foo" → Field("foo", Map("bar" → Field("bar", query = Map("pr" → "vr"))), Map("pf" → "vf")), "baz" → Field("baz", Map("b1" → Field("b1", query = Map("p1" → "v1")), "b2" → Field("b2")))))))
    }
    "return fields by getter" in {
      Field.read("name").get("name") must beSome(Field("name"))
      Field.read("name").get("ыы") must beNone
      Field.read("name.value").get("name") must beSome(Field("name", Map("value" → Field("value"))))
      Field.read("name.value").get("name.value") must beSome(Field("value"))
    }
    "respect strict fields" in {
      Field.read("name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), strict = false)))
      Field.read("name.!sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), strict = true)))
      Field.read("name.!sub,name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), strict = true)))
      Field.read("name.!{sub},name.sub") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), strict = true)))
      Field.read("name.!{sub}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")), strict = true)))
      Field.read("name.!{sub,other.!field}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"), "other" → Field("other", Map("field" → Field("field")), strict = true)), strict = true)))
    }
    "omit spaces" in {
      Field.read("name {\t}") must_== Field("$", Map("name" → Field("name")))
      Field.read("name. {  }") must_== Field("$", Map("name" → Field("name")))
      Field.read("name.{   sub}   ") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub")))))
      Field.read("name . {sub, foo , bar}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"), "foo" → Field("foo"), "bar" → Field("bar")))))
      Field.read("name . ! {sub,foo.bar, baz.! {b1 , b2}}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub"), "foo" → Field("foo", Map("bar" → Field("bar"))), "baz" → Field("baz", Map("b1" → Field("b1"), "b2" → Field("b2")), strict = true)), strict = true)))
      Field.read("name.{sub(p:v),foo(pf:vf).bar( pr : vr ) , baz      .\n\n{ b1(p1:v1),\nb2}\n\t}") must_== Field("$", Map("name" → Field("name", Map("sub" → Field("sub", query = Map("p" → "v")), "foo" → Field("foo", Map("bar" → Field("bar", query = Map("pr" → "vr"))), Map("pf" → "vf")), "baz" → Field("baz", Map("b1" → Field("b1", query = Map("p1" → "v1")), "b2" → Field("b2")))))))
    }
  }
}
