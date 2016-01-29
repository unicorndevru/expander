package expander.ast

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ OptionValues, TryValues, Matchers, WordSpec }

object FieldParser {
  import fastparse.all._

  private val spaces = P(CharIn(" \t\n").rep)
  private val name = P(spaces ~ CharIn("1234567890qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM").rep(min = 1).! ~ spaces)

  private val strict: Parser[Boolean] = P(spaces ~ "!".!.?.map(_.isDefined) ~ spaces)

  private val field: Parser[Field] = P(name ~ ("." ~ strict ~ P(fieldSingle | fieldSeq)).?.map(_.getOrElse(false → Map.empty[String, Field]))).map {
    case (n, (s, fs)) ⇒
      Field(n, strict = s, inners = fs)
  }

  private val fieldSingle: Parser[Map[String, Field]] = P(field ~ spaces).map(f ⇒ Map(f.name → f))
  private val fieldSeq: Parser[Map[String, Field]] = P(spaces ~ "{" ~ fields ~ "}" ~ spaces)

  private val fields: Parser[Map[String, Field]] = P(spaces ~ field.rep(sep = ",") ~ spaces).map {
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

  private def sumFields(fs1: Map[String, Field], fs2: Map[String, Field]): Map[String, Field] = {
    val commonKeys = fs1.keySet intersect fs2.keySet
    val values = (fs1.keySet -- commonKeys).map(fs1.apply) ++ (fs2.keySet -- commonKeys).map(fs2.apply) ++ commonKeys.map { k ⇒
      val f1 = fs1(k)
      val f2 = fs2(k)
      f1.copy(inners = sumFields(f1.inners, f2.inners) /*, query = f1.query ++ f2.query*/ )
    }
    values.map(f ⇒ f.name → f).toMap
  }

  def parse(line: String): Field = Field.Empty.copy(inners = fields.parse(line) match {
    case Result.Success(v, _) ⇒ v
    case _: Result.Failure    ⇒ Map.empty
  })
}

class FieldsSpec extends WordSpec with Matchers with ScalaFutures with TryValues
    with OptionValues {

  "field parse" should {
    "parse item by id" in {

      FieldParser.parse("user") should be(Field("$", Map("user" → Field("user"))))
      FieldParser.parse("user.sub") should be(Field("$", Map("user" → Field("user", Map("sub" → Field("sub"))))))

    }
  }

}
