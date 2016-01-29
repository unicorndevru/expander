package expander.ast

import fastparse.core.Result
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ OptionValues, TryValues, Matchers, WordSpec }

object FieldParser {
  private object Parse {

    import fastparse.all._

    val spaces = P(CharIn(" \t\n").rep)
    val name = P(spaces ~ CharIn("1234567890qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM").rep(min = 1).! ~ spaces)
    val value = P(spaces ~ CharIn("1234567890qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM-+_$%").rep(min = 1).! ~ spaces)

    val queryPair: Parser[(String, String)] = P(name ~ ":" ~ value)
    val queryParams: Parser[Map[String, String]] = P(queryPair.rep(sep = ",")).?.map(_.map(_.toMap).getOrElse(Map.empty))

    val queryContents: Parser[(Option[String], Map[String, String])] = P(P(value.map(Some(_)) ~ "|" ~ queryParams) | queryParams.map(Option.empty[String] → _) | value.map(v ⇒ Some(v) → Map.empty[String, String]))

    val query: Parser[Option[Expand]] = P("(" ~ queryContents ~ ")").filter(ps ⇒ ps._1.nonEmpty || ps._2.nonEmpty).map(ps ⇒ Expand(path = ps._1, params = ps._2)).?

    val strict: Parser[Boolean] = P(spaces ~ "!".!.?.map(_.isDefined) ~ spaces)

    val quantifier = P("[" ~ spaces ~ CharIn("1234567890").rep(min = 1).!.map(_.toInt)).?

    val fieldName = P(name ~ quantifier ~ query ~ spaces)

    val field: Parser[Field] = P(fieldName ~ (P("." ~ strict ~ fieldSingle) | P(strict ~ fieldSeq)).?.map(_.getOrElse(false → Map.empty[String, Field]))).map {
      case (n, q, ex, (s, fs)) ⇒
        Field(n, strict = s, inners = fs, quantifier = q, expand = ex)
    }

    private val fieldSingle: Parser[Map[String, Field]] = P(field ~ spaces).map(f ⇒ Map(f.name → f))
    val fieldSeq: Parser[Map[String, Field]] = P(spaces ~ "{" ~ fields ~ "}" ~ spaces)

    val fields: Parser[Map[String, Field]] = P(spaces ~ field.rep(sep = ",") ~ spaces).map {
      case fs ⇒
        fs.groupBy(_.name).foldLeft(Seq.empty[Field]) {
          case (acc, (_, n :: Nil)) ⇒ acc :+ n
          case (acc, (_, ns)) ⇒
            acc :+ ns.reduce[Field] {
              case (f1, f2) ⇒
                f1.copy(inners = sumFields(f1.inners, f2.inners) /*, query = f1.query ++ f2.query*/ )
            }
        }.map(n ⇒ n.name → n).toMap
    }

    def sumFields(fs1: Map[String, Field], fs2: Map[String, Field]): Map[String, Field] = {
      val commonKeys = fs1.keySet intersect fs2.keySet
      val values = (fs1.keySet -- commonKeys).map(fs1.apply) ++ (fs2.keySet -- commonKeys).map(fs2.apply) ++ commonKeys.map { k ⇒
        val f1 = fs1(k)
        val f2 = fs2(k)
        f1.copy(inners = sumFields(f1.inners, f2.inners) /*, query = f1.query ++ f2.query*/ )
      }
      values.map(f ⇒ f.name → f).toMap
    }

  }

  def parse(line: String): Field = Field.Empty.copy(inners = Parse.fields.parse(line) match {
    case Result.Success(v, _) ⇒ v
    case _: Result.Failure    ⇒ Map.empty
  })
}

class FieldsSpec extends WordSpec with Matchers with ScalaFutures with TryValues
    with OptionValues {

  "field parse" should {
    "parse subfields" in {

      FieldParser.parse("user") should be(Field("$", Map("user" → Field("user"))))
      FieldParser.parse("user.sub") should be(Field("$", Map("user" → Field("user", Map("sub" → Field("sub"))))))
      FieldParser.parse("user{sub}") should be(Field("$", Map("user" → Field("user", Map("sub" → Field("sub"))))))
      FieldParser.parse("user.some { sub, other { apple } }") should be(Field("$", Map("user" → Field("user", Map("some" → Field("some", Map("sub" → Field("sub"), "other" → Field("other", Map("apple" → Field("apple"))))))))))

    }

    "parse quantifier" in {

      FieldParser.parse("user[0]") should be(Field("$", Map("user" → Field("user", quantifier = Some(0)))))
      FieldParser.parse("user[39]") should be(Field("$", Map("user" → Field("user", quantifier = Some(39)))))
      FieldParser.parse("some.user[39]") should be(Field("$", Map("some" → Field("some", Map("user" → Field("user", quantifier = Some(39)))))))
      FieldParser.parse("some[1].user[39]") should be(Field("$", Map("some" → Field("some", Map("user" → Field("user", quantifier = Some(39))), quantifier = Some(1)))))
      FieldParser.parse("some[ 1 ] { user [ 39]\n}") should be(Field("$", Map("some" → Field("some", Map("user" → Field("user", quantifier = Some(39))), quantifier = Some(1)))))

    }

    "parse expand" in {
      FieldParser.parse("user()") should be(Field("$", Map("user" → Field("user"))))
      FieldParser.parse("user(path)") should be(Field("$", Map("user" → Field("user", expand = Some(Expand(Some("path")))))))
    }
  }

}
