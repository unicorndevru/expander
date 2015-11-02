package psgr.expander.play

import com.google.inject.ImplementedBy
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import psgr.expander.core.ResolveResult

import scala.concurrent.Future

@ImplementedBy(classOf[NoopMetaResolveInterceptor])
trait MetaResolveInterceptor {
  def apply(result: ResolveResult[JsValue])(implicit rh: RequestHeader): Future[ResolveResult[JsValue]]
}

class NoopMetaResolveInterceptor extends MetaResolveInterceptor {
  override def apply(result: ResolveResult[JsValue])(implicit rh: RequestHeader) = Future successful result
}