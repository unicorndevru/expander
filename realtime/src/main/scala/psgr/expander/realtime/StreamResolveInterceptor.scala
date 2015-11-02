package psgr.expander.realtime

import javax.inject.Inject

import play.api.libs.json._
import play.api.mvc.RequestHeader
import psgr.expander.core.ResolveResult
import psgr.expander.play.MetaResolveInterceptor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StreamResolveInterceptor @Inject() (streamService: StreamsService) extends MetaResolveInterceptor {
  override def apply(result: ResolveResult[JsValue])(implicit rh: RequestHeader) = {
    result.value match {
      case _ if result.refs.isEmpty || (rh.getQueryString("_withStream").isEmpty && !rh.headers.get("Accept").exists(_.contains("_withStream"))) ⇒
        Future.successful(result)

      case jo: JsObject ⇒
        streamService.start(result.refs).map { streamId ⇒
          result.copy(value = jo deepMerge Json.obj("meta" → Json.obj("stream" → ("/api/streams/" + streamId))))
        }

      case _ ⇒
        Future.successful(result)
    }
  }
}
