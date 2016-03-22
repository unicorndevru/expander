package expander.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.util.ByteString
import expander.core.{ ExpandContext, Expander, PathRequest, ResourceContext }
import play.api.libs.json.{ JsPath, JsValue, Json, Writes }

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ ExecutionContext, Future }

class JsonExpandResolveContext(headers: collection.immutable.Seq[HttpHeader], expandContextProvider: JsValue ⇒ Map[JsPath, String], system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends ExpandContext[JsValue] {
  ctx ⇒

  val http = Http(system)

  val cache = TrieMap[Uri, Future[JsValue]]()

  override def resources(root: JsValue) = expandContextProvider(root).mapValues { url ⇒
    ResourceContext[JsValue] {
      params: Seq[(String, String)] ⇒

        val (continueExpand, passParams) = params.partition(_._1 == Expander.Key)

        val uri: Uri = url
        val q = passParams.foldLeft(uri.query())(_.+:(_))
        val rUri = uri.withQuery(q)

        println("request: " + rUri)

        val jsonF = cache.getOrElseUpdate(rUri, http.singleRequest(HttpRequest(uri = rUri, headers = headers)).flatMap {
          case HttpResponse(_, hs, entity, _) if entity.contentType == ContentTypes.`application/json` ⇒
            println("response ok: " + entity)
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(bs ⇒ Json.parse(bs.decodeString("UTF-8")))

          case HttpResponse(status, _, _, _) ⇒
            println("response strange: " + status)
            Future.successful(Json.obj("desc" → status.reason(), "status" → status.intValue()))
        }.map { r ⇒
          r
        })

        if (continueExpand.isEmpty) {
          jsonF
        } else {
          val continueReqs = PathRequest.parse(continueExpand.map(_._2).mkString(","))
          if (continueReqs.isEmpty) {
            jsonF
          } else {
            jsonF.flatMap { json ⇒
              Expander(json, continueReqs: _*)(ctx, Writes.of[JsValue])
            }
          }
        }

    }

  }
}
