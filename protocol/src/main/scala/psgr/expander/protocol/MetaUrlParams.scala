package psgr.expander.protocol

import java.net.URLEncoder

object MetaUrlParams {
  def encode(prefix: String = "?")(params: Seq[(String, String)]): String =
    if (params.isEmpty) {
      ""
    } else params.map {
      case (k, v) ⇒ k + "=" + URLEncoder.encode(v, "UTF-8")
    }.foldLeft(prefix) {
      case (`prefix`, pair) ⇒
        prefix + pair
      case (q, pair) ⇒
        q + "&" + pair
    }
}
