package psgr.expander.protocol

import play.api.libs.json._

import scala.language.implicitConversions
import scala.reflect.ClassTag

class MetaId[+T](val href: String, val mediaType: Option[MediaType] = None) {
  def withHref(nhref: String) = new MetaId[T](href = nhref, mediaType = mediaType)

  def appendHref(nhref: String) = withHref(href + nhref)

  override def equals(obj: scala.Any): Boolean = obj match {
    case v: MetaId[T] ⇒
      v.href == href && v.mediaType == mediaType
    case _ ⇒
      false
  }

  override def hashCode(): Int = href.hashCode + mediaType.hashCode()

  override def toString: String = s"$href($mediaType)"
}

object MetaId {
  private val empty = Json.obj()

  private[protocol] val r: Reads[(String, Option[MediaType])] = Reads {
    json ⇒
      (json \ "href").validate[String].map(_ → (json \ "mediaType").asOpt[MediaType])
  }
  private[protocol] val w: Writes[(String, Option[MediaType])] = Writes[(String, Option[MediaType])] {
    case (href, mediaType) ⇒
      mediaType.fold(empty)(media ⇒ Json.obj("mediaType" → media)) + ("href" → JsString(href))
  }

  implicit def metaIdFmt[T]: Format[MetaId[T]] = Format(
    Reads { json ⇒ r.reads(json).map(fs ⇒ new MetaId[T](fs._1, fs._2)) },
    Writes[MetaId[T]] {
      id ⇒ w.writes(id.href, id.mediaType)
    }
  )

  def apply[T](href: String, mediaType: MediaType): MetaId[T] = new MetaId[T](href, Some(mediaType))

  def apply[T: ClassTag](href: String, v: Int): MetaId[T] = apply[T](href, MediaType.json[T].v(v))

  implicit def fromMetaRef[T](ref: MetaRef[T]): MetaId[T] = ref.meta
}

case class MetaRef[+T] private[expander] (meta: MetaId[T])

object MetaRef {
  implicit def metaRefFmt[T]: OFormat[MetaRef[T]] = OFormat(
    Reads[MetaRef[T]] { json ⇒ (json \ "meta").validate[MetaId[T]].map(MetaRef(_)) },
    OWrites[MetaRef[T]] { ref ⇒ Json.obj("meta" → ref.meta) }
  )

  implicit def fromMetaId[T](id: MetaId[T]): MetaRef[T] = MetaRef[T](id)
}

trait MetaBox {
  def meta: MetaId[_]
}

trait MetaResource[T] extends MetaBox {
  self: T ⇒
  def meta: MetaId[T]
}

trait MetaItems[T] extends MetaBox {
  def meta: MetaId[_ <: MetaItems[T]]

  def items: Seq[T]
}