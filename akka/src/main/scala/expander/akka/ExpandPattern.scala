package expander.akka

import play.api.libs.json.JsPath

case class ExpandPattern(
  url:      String,
  path:     JsPath,
  urlKeys:  Set[String],
  required: Map[String, JsPath],
  optional: Map[String, JsPath] = Map.empty,
  applied:  Map[String, String] = Map.empty
)
