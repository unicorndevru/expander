package psgr.expander.core

case class Field(name: String, inner: Map[String, Field] = Map.empty, query: Map[String, String] = Map.empty, strict: Boolean = false) {
  def mkString(withSelf: Boolean = true): String = if (withSelf) {
    val s = name + (if (query.isEmpty) "" else s"(${query.map(kv ⇒ kv._1 + ":" + kv._2).mkString(",")})")
    inner.size match {
      case 1 ⇒
        s + "." + (if (strict) "!" else "") + inner.values.head.mkString(true)
      case sz if sz > 1 ⇒
        s + "." + (if (strict) "!" else "") + "{" + inner.values.map(_.mkString(true)).mkString(",") + "}"
      case _ ⇒
        s
    }
  } else if (inner.nonEmpty) inner.values.map(_.mkString(true)).mkString(",")
  else ""

  def isEmpty = inner.isEmpty && name == "$"

  def nonEmpty = !isEmpty

  private def getInner(path: List[String]): Option[Field] = path match {
    case s :: tail ⇒
      inner.get(s).flatMap(_.getInner(tail))
    case Nil ⇒
      Some(this)
  }

  def get(s: String): Option[Field] = getInner(s.split('.').toList)

  def contains(s: String): Boolean = get(s).isDefined
}

object Field {
  val RootPath = "$"

  def read(s: String*): Field = FieldParse.read(s: _*)

  def empty: Field = Field(RootPath)
}

