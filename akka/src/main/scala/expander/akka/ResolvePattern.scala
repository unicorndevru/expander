package expander.akka

import play.api.libs.json.JsPath

case class ResolvePattern(
  url:      String,
  path:     JsPath,
  required: Map[String, JsPath],
  optional: Map[String, JsPath] = Map.empty,
  query:    Set[String]         = Set.empty,
  applied:  Map[String, String] = Map.empty
)
