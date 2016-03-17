package expander.akka

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes, StatusCode }
import akka.http.scaladsl.server.{ RequestContext, Directive1 }
import akka.http.scaladsl.server.Directives._
import akka.util.ByteString
import expander.core.{ ExpandContext, Expander, PathRequest }
import play.api.libs.json.{ JsValue, Json, Writes }
import scala.language.implicitConversions

trait ExpanderDirectives {

  trait ExpandMagnet {
    def expand(expandRequestOpt: Option[String]): Directive1[JsValue]
  }

  type RequestExpandContext[T] = RequestContext ⇒ ExpandContext[T]

  implicit def implExpandContext[T](ec: ExpandContext[T]): RequestExpandContext[T] = _ ⇒ ec

  private class ExpandMagnetImpl[T](root: T, w: Writes[T], ec: RequestExpandContext[T]) extends ExpandMagnet {
    override def expand(expandRequestOpt: Option[String]) = expandRequestOpt match {
      case Some(expandRequest) ⇒
        extractRequestContext.flatMap { reqCtx ⇒
          implicit val ctx = ec(reqCtx)
          onSuccess(Expander(root, PathRequest.parse(expandRequest): _*)(ctx, w))
        }
      case None ⇒
        provide(w.writes(root))
    }
  }

  implicit def wrapExpand[T](root: T)(implicit w: Writes[T], ec: ExpandContext[T]): ExpandMagnet = new ExpandMagnetImpl[T](root, w, ec)

  def expandJson(magnet: ExpandMagnet): Directive1[JsValue] =
    parameter(Expander.Key.?).flatMap(magnet.expand)

  def expand(magnet: ExpandMagnet, status: StatusCode = StatusCodes.OK) =
    expandJson(magnet) { json ⇒
      complete(status → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.stringify(json))))
    }
}