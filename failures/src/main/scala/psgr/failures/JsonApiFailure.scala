package psgr.failures

import play.api.libs.json.{ JsValue, Json, Format, __ }

/**
 * @author alari
 * @since 6/9/14
 */
case class JsonApiFailure(status: Int, code: String, summary: String, service: String, data: Option[JsValue] = None) extends Throwable {
  override def toString = s"$status($service/$code): $summary / $data"

  override def getMessage: String = toString
}

object JsonApiFailure {
  implicit val f: Format[JsonApiFailure] = (__ \ "failure").format(Json.format[JsonApiFailure])
}