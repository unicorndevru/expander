package psgr.expander.protocol

import play.api.libs.json._

case class MetaPatch[T](
    meta:     MetaId[T],
    $set:     Option[JsObject] = None,
    $unset:   Option[JsObject] = None,
    $append:  Option[JsObject] = None,
    $prepend: Option[JsObject] = None,
    $pull:    Option[JsObject] = None
) extends MetaBox {
  def isEmpty = $set.isEmpty && $unset.isEmpty

  def nonEmpty = !isEmpty

  private def changeArray[K: Writes](k: String, v: K, current: Option[JsObject])(set: Option[JsObject] ⇒ MetaPatch[T]) =
    set(Some {
      val p = current.getOrElse(Json.obj())
      p + (k, p.value.get(k) match {
        case Some(a: JsArray) ⇒
          a :+ Json.toJson(v)
        case _ ⇒
          Json.arr(v)
      })
    })

  def pull[K: Writes](k: String, v: K) =
    changeArray(k, v, $pull)(p ⇒ copy($pull = p))

  def append[K: Writes](k: String, v: K) =
    changeArray(k, v, $append)(p ⇒ copy($append = p))

  def prepend[K: Writes](k: String, v: K) =
    changeArray(k, v, $prepend)(p ⇒ copy($prepend = p))

}

object MetaPatch {
  implicit def fmt[T]: OFormat[MetaPatch[T]] = OFormat(
    Reads[MetaPatch[T]] { json ⇒
      (json \ "meta").validate(MetaId.r).map(som ⇒ new MetaId[T](som._1, som._2)).map {
        mid ⇒
          MetaPatch[T](
            meta = mid,
            $set = (json \ "$set").asOpt[JsObject],
            $unset = (json \ "$unset").asOpt[JsObject],
            $append = (json \ "$append").asOpt[JsObject],
            $prepend = (json \ "$prepend").asOpt[JsObject],
            $pull = (json \ "$pull").asOpt[JsObject]
          )
      }

    },
    OWrites[MetaPatch[T]] { mp ⇒
      (__ \ "meta").write(MetaId.w).writes(mp.meta.href, mp.meta.mediaType) ++
        (__ \ "$set").writeNullable[JsObject].writes(mp.$set) ++
        (__ \ "$unset").writeNullable[JsObject].writes(mp.$unset) ++
        (__ \ "$append").writeNullable[JsObject].writes(mp.$append) ++
        (__ \ "$prepend").writeNullable[JsObject].writes(mp.$prepend) ++
        (__ \ "$pull").writeNullable[JsObject].writes(mp.$pull)
    }
  )

  def apply[T <: MetaResource[T]: Writes](a: T, b: T): MetaPatch[T] = {
    val meta = a.meta

    val aJson = implicitly[Writes[T]].writes(a).asInstanceOf[JsObject]
    val bJson = implicitly[Writes[T]].writes(b).asInstanceOf[JsObject]

    val addedKeys = bJson.value.keySet -- aJson.value.keySet
    val removedKeys = aJson.value.keySet -- bJson.value.keySet

    val changedKeys: Seq[Either[String, (String, (Seq[JsValue], Seq[JsValue], Seq[JsValue]))]] = (aJson.value.keySet intersect bJson.value.keySet).toSeq.collect {
      case k if bJson.value(k) != aJson.value(k) ⇒
        (aJson.value(k), bJson.value(k)) match {
          case (a: JsArray, b: JsArray) ⇒
            val (rest, pull) = a.value.partition(b.value.contains)

            if (rest.isEmpty) {
              Left(k)
            } else {
              val i = b.value.indexOfSlice(rest)

              if (i != -1) {
                val prepend = b.value.take(i)
                val append = b.value.drop(i + rest.size)

                Right(k, (pull, prepend, append))
              } else Left(k)
            }

          case _ ⇒
            Left(k)
        }
    }

    val setValueKeys = changedKeys.collect {
      case Left(k) ⇒ k
    }

    val pullPush = changedKeys.collect {
      case Right(v) ⇒ v
    }

    val pull = pullPush.collect {
      case (k, (p, _, _)) if p.nonEmpty ⇒ k → JsArray(p)
    }
    val prepend = pullPush.collect {
      case (k, (_, p, _)) if p.nonEmpty ⇒ k → JsArray(p)
    }
    val append = pullPush.collect {
      case (k, (_, _, p)) if p.nonEmpty ⇒ k → JsArray(p)
    }

    val setKeys = addedKeys ++ setValueKeys

    val set = bJson.value.filterKeys(setKeys)
    val unset = removedKeys.map(_ → JsBoolean(true)).toMap

    MetaPatch(
      meta = meta,
      $set = if (set.isEmpty) None else Some(JsObject(set)),
      $unset = if (unset.isEmpty) None else Some(JsObject(unset)),
      $append = if (append.isEmpty) None else Some(JsObject(append)),
      $prepend = if (prepend.isEmpty) None else Some(JsObject(prepend)),
      $pull = if (pull.isEmpty) None else Some(JsObject(pull))
    )
  }
}