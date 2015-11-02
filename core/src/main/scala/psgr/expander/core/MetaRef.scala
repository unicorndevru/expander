package psgr.expander.core

import play.api.libs.json._

case class MetaRef(
  href:      String,
  path:      Option[String] = None,
  mediaType: Option[String] = None
)

object MetaRef {
  implicit val f: OFormat[MetaRef] = (__ \ "meta").format(Json.format[MetaRef])
}