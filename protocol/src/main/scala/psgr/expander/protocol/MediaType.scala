package psgr.expander.protocol

import play.api.libs.json._

import scala.reflect.ClassTag

case class MediaType(top: String = "application", subtype: String = "json", prod: Option[String] = None, params: Seq[(String, String)] = Seq.empty) {
  override def toString = s"$top/${prod.fold(subtype)(_ + "+" + subtype)}${MetaUrlParams.encode(";")(params)}"

  def product(s: String) = copy(prod = Some(s))

  def v(i: Int) = copy(params = ("v" → i.toString) +: params)

  def expand(ps: String) = copy(params = params :+ ("expand" → ps))
}

object MediaType {
  private val pattern = "^(application|audio|example|image|message|model|multipart|text|video)/(([^+ ]+)\\+)?(xml|json|ber|der|fastinfoset|wbxml|zip)(;([^ ]+))?".r

  def read(s: String): Option[MediaType] = s match {
    case pattern(top, _, product, subtype, _, params) ⇒
      val ps = if (params != null && params.nonEmpty) params.split('&').toSeq.map(s ⇒ s.splitAt(s.indexOf('='))).map(kv ⇒ kv.copy(_2 = kv._2.drop(1))) else Seq.empty
      Some(MediaType(top, subtype, Option(product).filter(_.nonEmpty), ps))
    case _ ⇒
      None
  }

  implicit val writes: Writes[MediaType] = Writes[MediaType] { mt ⇒ JsString(mt.toString) }

  implicit val reads: Reads[MediaType] = Reads[MediaType] {
    case JsString(mt) ⇒
      read(mt) match {
        case Some(t) ⇒ JsSuccess(t)
        case None    ⇒ JsError("wrong.format")
      }
    case _ ⇒
      JsError("string.expected")
  }

  private def camelToDashes(name: String): String = name.replaceAll("(.)(\\p{Upper})", "$1-$2").toLowerCase

  def json[T: ClassTag]: MediaType =
    MediaType(prod = Some(camelToDashes(implicitly[ClassTag[T]].runtimeClass.getSimpleName)))

}
