package expander.core

import play.api.libs.json.JsValue

import scala.concurrent.Future

trait ResourceContext[T] {
  def resolve(params: Map[String, String]): Future[JsValue]
}
