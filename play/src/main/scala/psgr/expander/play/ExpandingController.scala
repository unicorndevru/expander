package psgr.expander.play

import javax.inject.Inject

import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.{ Controller, RequestHeader, ResponseHeader, Result }
import psgr.expander.core.Field
import psgr.expander.protocol.MetaBox

trait ExpandingController {
  self: Controller ⇒

  @Inject private var metaResolver: MetaResolver = null

  private val jsonWriteable = implicitly[Writeable[JsValue]]

  def getExpandFields(implicit rh: RequestHeader): Field = metaResolver.getFields(rh)

  implicit class JsonResult[T: Writes](res: T) {
    def json = Json.toJson(res)

    def contentType = res match {
      case r: MetaBox ⇒
        r.meta.mediaType.map(_.toString).orElse(jsonWriteable.contentType)
      case _ ⇒
        jsonWriteable.contentType
    }

    def contentTypeExpand(f: Field) = res match {
      case r: MetaBox ⇒
        r.meta.mediaType.map(_.expand(f.mkString(false)).toString).orElse(jsonWriteable.contentType)
      case _ ⇒
        jsonWriteable.contentType
    }

    def ok(implicit rh: RequestHeader): Result = result(200)

    def created(implicit rh: RequestHeader): Result = result(201)

    def result(status: Int)(implicit rh: RequestHeader): Result =
      json match {
        case jo: JsObject ⇒
          val cte = metaResolver.getFields(rh) match {
            case fields if fields.nonEmpty ⇒
              contentTypeExpand(fields)
            case _ ⇒
              contentType
          }

          val body = metaResolver.expandEnum(cte.fold(jo)(ct ⇒ jo deepMerge Json.obj("meta" → Json.obj("mediaType" → ct))))
          val ct = cte.getOrElse("application/json;ex")

          rh.queryString.contains("_noContent") match {
            case true ⇒
              NoContent.withHeaders(metaResolver.expandedHeader → System.currentTimeMillis().toString)
            case false ⇒
              Result(
                ResponseHeader(status),
                body
              ).as(ct).withHeaders(metaResolver.expandedHeader → System.currentTimeMillis().toString)
          }

        case _ ⇒
          rh.queryString.contains("_noContent") match {
            case true ⇒
              NoContent
            case false ⇒
              Status(status)(json)
          }
      }
  }

}
