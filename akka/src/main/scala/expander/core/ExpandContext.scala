package expander.core

import play.api.libs.json.JsPath

trait ExpandContext[T] {
  def resources(root: T): Map[JsPath, ResourceContext[_]]
}
