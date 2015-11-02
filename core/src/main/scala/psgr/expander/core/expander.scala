package psgr.expander.core

import play.api.libs.json._

import scala.concurrent.Future

sealed trait FieldResolveResult {
  def value: JsObject
}

case class FieldResolveSuccess(ref: MetaRef, value: JsObject) extends FieldResolveResult

case class FieldResolveFailure(value: JsObject) extends FieldResolveResult

case class ResolveResult[+T <: JsValue](refs: Set[MetaRef], value: T)

object JsonExpander {
  def apply(obj: JsObject, field: Field): JsonExpander = JsonExpander(Field.RootPath, obj, field)

  def apply(obj: JsObject, fields: String*): JsonExpander = JsonExpander(Field.RootPath, obj, Field.read(fields: _*))
}

case class JsonExpander(path: String, obj: JsObject, field: Field) {
  private lazy val innerKeys = field.inner.keySet + "meta"

  private def normalizeObj(o: JsObject): JsObject =
    if (field.strict) {
      JsObject(o.value.filterKeys(innerKeys))
    } else {
      o
    }

  private def normalizeResolved(o: JsObject) = normalizeObj(o) deepMerge Json.obj("meta" → Json.obj("path" → path))

  private def resolved(implicit resolver: JsonResolver): JsonResolvedExpander =
    JsonResolvedExpander(
      path,
      obj.validate[MetaRef].asOpt match {
        case Some(ref) if obj.keys.size == 1 ⇒
          implicit val ctx = resolver.ctx
          resolver(ref, field).map {
            case s: FieldResolveSuccess ⇒
              s.copy(value = normalizeResolved(s.value))
            case f ⇒ f
          }

        case Some(ref) ⇒
          Future successful FieldResolveSuccess(ref, normalizeResolved(obj))

        case None ⇒
          Future successful FieldResolveFailure(normalizeObj(obj))

      },
      field.inner
    )

  def expand(implicit resolver: JsonResolver): Future[ResolveResult[JsValue]] =
    resolved.expand
}

private[core] case class JsonResolvedExpander(path: String, objF: Future[FieldResolveResult], fields: Map[String, Field]) {
  def expand(implicit resolver: JsonResolver): Future[ResolveResult[JsValue]] = {

    implicit val ctx = resolver.ctx

    val actualPath = if (path == Field.RootPath) "" else path + "."

    def r(obj: JsObject) = Future.traverse(obj.keys.intersect(fields.keySet)) { f ⇒

      val fieldHandler = fields(f)

      val valueF: Future[ResolveResult[JsValue]] = (obj \ f).get match {
        case o: JsObject ⇒
          JsonExpander(actualPath + f, o, fieldHandler).expand
        case JsArray(ja) ⇒
          Future.traverse(ja.seq.zipWithIndex) {
            case (o: JsObject, i) ⇒
              JsonExpander(s"$actualPath$f[$i]", o, fieldHandler).expand
            case (j, i) ⇒
              Future.successful(ResolveResult(Set.empty, j))
          }.map { rs ⇒
            ResolveResult(rs.map(_.refs).foldLeft(Set.empty[MetaRef])(_ ++ _), JsArray(rs.map(_.value)))
          }
        case j ⇒
          Future successful ResolveResult(Set.empty, j)
      }

      valueF.map(f → _)

    }.map(_.foldLeft(ResolveResult(Set.empty, obj)) {
      case (host, add) ⇒
        ResolveResult(host.refs ++ add._2.refs, host.value + (add._1 → add._2.value))
    })

    objF flatMap {
      case FieldResolveSuccess(ref, body) ⇒
        r(body).map(rlv ⇒ rlv.copy(refs = rlv.refs + ref.copy(path = Some(path))))

      case FieldResolveFailure(body) ⇒
        r(body)
    }

  }
}