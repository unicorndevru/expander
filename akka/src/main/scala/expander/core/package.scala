package expander

import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

package object core {

  trait ResolveById[T] {
    def getById(id: String): Future[T]
  }

  trait ResourceContext[T] {
    def apply(paths: Seq[JsPath]): Future[JsValue]
  }

  trait ExpandContext[T] {
    protected def leaf[TT: Writes: ResolveById](id: String) =
      new ResourceContext[TT] {
        override def apply(paths: Seq[JsPath]) = implicitly[ResolveById[TT]].getById(id).map(implicitly[Writes[TT]].writes)
      }

    protected def ref[TT: Writes: ResolveById: ExpandContext](id: String) =
      new ResourceContext[TT] {
        override def apply(paths: Seq[JsPath]) = implicitly[ResolveById[TT]].getById(id).flatMap(expand(_, paths: _*))
      }

    def resources(root: T): Map[JsPath, ResourceContext[_]]
  }

  def pathMatch(request: JsPath, given: JsPath): Option[JsPath] = {

    if (request.path.isEmpty) None
    else if (request.path.startsWith(given.path)) Some(JsPath(request.path.drop(given.path.size)))
    else if (given.path.startsWith(request.path)) Some(JsPath())
    else {
      val searchIndex = request.path.indexWhere(_.isInstanceOf[RecursiveSearch])
      val idxIndex = given.path.indexWhere(_.isInstanceOf[IdxPathNode])

      if (searchIndex >= 0 && idxIndex == searchIndex && request.path.take(searchIndex) == given.path.take(searchIndex)) {
        Some(JsPath(KeyPathNode(request.path(searchIndex).asInstanceOf[RecursiveSearch].key) +: request.path.drop(searchIndex + 1)))
      } else None
    }
  }

  def expand[T](root: T, paths: JsPath*)(implicit expandContext: ExpandContext[T], rootWrites: Writes[T]): Future[JsValue] = {
    val resources = expandContext.resources(root)
    val rootJson = Json.toJson(root)

    val pathMatches = resources.keys.map(p ⇒ p → paths.flatMap(pathMatch(_, p))).toMap

    if (pathMatches.forall(_._2.isEmpty)) {
      Future.successful(rootJson)
    } else Future sequence resources.filterKeys(pathMatches(_).nonEmpty).toSeq.map {
      case (k, v) ⇒
        v(pathMatches(k)).map(k → _)
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
