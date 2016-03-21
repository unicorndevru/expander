package expander.core

import play.api.libs.json.JsValue

import scala.concurrent.Future

trait ResourceContext[T] {
  def resolve(params: Seq[(String, String)]): Future[JsValue]
}

object ResourceContext {
  def apply[T](f: Seq[(String, String)] ⇒ Future[JsValue]): ResourceContext[T] = new ResourceContext[T] {
    override def resolve(params: Seq[(String, String)]) = f(params)
  }
}