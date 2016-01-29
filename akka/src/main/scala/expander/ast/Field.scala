package expander.ast

case class Expand(
  path:   String,
  params: Map[String, String]
)

case class Field(
  name:       String,
  inners:     Map[String, Field] = Map.empty,
  strict:     Boolean            = false,
  quantifier: Option[Int]        = None,
  expand:     Option[Expand]     = None
)

object Field {
  val Empty = Field("$")
}