package expander.core

import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Expander {

  val Key = "_expand"

  def mergeArr(l: JsArray, r: JsArray): JsArray =
    JsArray(l.value zip r.value map {
      case (ae: JsObject, ao: JsObject) ⇒
        mergeObj(ae, ao)
      case (ae: JsArray, ao: JsArray) ⇒
        mergeArr(ae, ao)
      case (_, ao) ⇒ ao
    })

  def mergeObj(l: JsObject, r: JsObject): JsObject = {
    JsObject((l.value ++ r.value).map {
      case (key, newValue) ⇒
        val maybeExistingValue = l.value.get(key)
        key → maybeExistingValue.fold(newValue)(ex ⇒ (ex, newValue) match {
          case (e: JsObject, o: JsObject) ⇒ mergeObj(e, o)
          case (a: JsArray, o: JsArray)   ⇒ mergeArr(a, o)
          case _                          ⇒ newValue
        })
    })
  }

  def foldArr(v: JsValue): JsValue = v match {
    case o: JsObject if o.value.nonEmpty && o.value.keys.forall(_.startsWith("$_")) ⇒
      JsArray(o.value.map {
        case (k, vv) ⇒ k.stripPrefix("$_").toInt → foldArr(vv)
      }.toSeq.sortBy(_._1).map(_._2))
    case o: JsObject ⇒
      JsObject(o.value.mapValues(foldArr))
    case a: JsArray ⇒
      JsArray(a.value.map(foldArr))
    case _ ⇒ v
  }

  def merge(l: JsObject, r: JsObject): JsObject = {
    val obj = mergeObj(l, r)
    JsObject(obj.value.mapValues(foldArr))
  }

  private def setInsideArray(tail: JsPath, v: JsValue): JsValue = if (tail.path.isEmpty) v else if (tail.path.forall(_.isInstanceOf[KeyPathNode])) tail.json.put(v).reads(Json.obj()).getOrElse(Json.obj()) else {
    val safePath = JsPath(tail.path.takeWhile(p ⇒ !p.isInstanceOf[IdxPathNode]))
    val subIdx = tail.path(safePath.path.length).asInstanceOf[IdxPathNode]
    val unsafeTail = JsPath(tail.path.drop(safePath.path.length + 1))
    safePath.json.put(Json.obj(s"$$_${subIdx.idx}" → setInsideArray(unsafeTail, v))).reads(Json.obj()).getOrElse(Json.obj())
  }

  def apply[T](root: T, reqs: PathRequest*)(implicit expandContext: ExpandContext[T], rootWrites: Writes[T]): Future[JsValue] = {
    val resources = expandContext.resources(root)
    val rootJson = Json.toJson(root)

    val pathMatches = resources.keys.map(p ⇒ p → reqs.flatMap(_.matchParams(p))).toMap

    if (pathMatches.forall(_._2.isEmpty)) {
      // nothing to resolve
      Future.successful(rootJson)
    } else Future sequence resources.filterKeys(pathMatches(_).nonEmpty).toSeq.map {
      case (k, v) ⇒
        // launch resolves
        v.resolve(pathMatches(k).reduce(_ ++ _)).map(k → _)
    } map {
      rs ⇒
        val (keyPaths, arrPaths) = rs.partition(_._1.path.forall(_.isInstanceOf[KeyPathNode]))

        val arrToKeyPaths = arrPaths.map {
          case (p, v) ⇒
            val kp = p.path.takeWhile(_.isInstanceOf[KeyPathNode])
            val idx = p.path(kp.size)
            val tail = p.path.drop(kp.size + 1)

            (JsPath(kp), (idx, JsPath(tail), v))
        }.groupBy(_._1).map {
          case (kp, vs) ⇒
            val mapV = vs.collect {
              case (_, (IdxPathNode(idx), tail, v)) ⇒
                idx → setInsideArray(tail, v)
            }.groupBy(_._1).map {
              case (i, ivs) ⇒
                i → ivs.map(_._2).reduce[JsValue]{
                  case (o1: JsObject, o2: JsObject) ⇒ merge(o1, o2)
                  case (ov, _)                      ⇒ ov
                }
            }
            val maxK = mapV.keySet.max

            (kp, JsArray((0 to maxK).map(mapV.getOrElse(_, Json.obj()))))
        }.toSeq

        (keyPaths ++ arrToKeyPaths).foldLeft(rootJson.as[JsObject]) {
          case (jo, (p, v)) ⇒
            merge(jo, p.json.put(v).reads(Json.obj()).getOrElse(Json.obj()))
        }
    }
  }
}
