package expander.core

import expander.ast.PathRequest
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Expander {

  def apply[T](root: T, reqs: PathRequest*)(implicit expandContext: ExpandContext[T], rootWrites: Writes[T]): Future[JsValue] = {
    val resources = expandContext.resources(root)
    val rootJson = Json.toJson(root)

    val pathMatches = resources.keys.map(p ⇒ p → reqs.flatMap(_.matchParams(p))).toMap

    if (pathMatches.forall(_._2.isEmpty)) {
      Future.successful(rootJson)
    } else Future sequence resources.filterKeys(pathMatches(_).nonEmpty).toSeq.map {
      case (k, v) ⇒
        v.resolve(pathMatches(k).reduce(_ ++ _)).map(k → _)
    } map {
      rs ⇒
        val (keyPaths, arrPaths) = rs.span(_._1.path.forall(_.isInstanceOf[KeyPathNode]))

        val arrToKeyPaths = arrPaths.map {
          case (p, v) ⇒
            val kp = p.path.takeWhile(_.isInstanceOf[KeyPathNode])
            val idx = p.path(kp.size)
            val tail = p.path.drop(kp.size + 1)

            (JsPath(kp), (idx, JsPath(tail), v))
        }.groupBy(_._1).map {
          case (kp, vs) ⇒
            val arrV = vs.collect {
              case (_, (IdxPathNode(idx), tail, v)) ⇒
                idx → (if (tail.path.isEmpty) v else tail.json.put(v).reads(Json.obj()).getOrElse(Json.obj()))
            }.sortBy(_._1).map(_._2)
            (kp, JsArray(arrV))
        }.toSeq

        (keyPaths ++ arrToKeyPaths).foldLeft(rootJson.as[JsObject]) {
          case (jo, (p, v)) ⇒
            jo deepMerge p.json.put(v).reads(Json.obj()).getOrElse(Json.obj())
        }
    }
  }
}
