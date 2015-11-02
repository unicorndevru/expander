package psgr.expander.realtime.core

import play.api.libs.json.JsValue

case class StreamEvent(id: Int, value: JsValue)