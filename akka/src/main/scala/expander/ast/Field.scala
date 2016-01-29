package expander.ast

case class Expand(
  path:   Option[String]      = None,
  params: Map[String, String] = Map.empty
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