package expander.core

import play.api.libs.json.JsPath

trait ExpandContext[T] {
  def resources(root: T): Map[JsPath, ResourceContext[_]]
}

object ExpandContext {
  def apply[T](f: T ⇒ Map[JsPath, ResourceContext[_]]): ExpandContext[T] = new ExpandContext[T] {
    override def resources(root: T) = f(root)
  }
}