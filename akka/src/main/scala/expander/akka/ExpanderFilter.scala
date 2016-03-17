package expander.akka

import java.security.MessageDigest

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives.{ `max-age`, `must-revalidate` }
import akka.http.scaladsl.model.headers.{ EntityTag, `Cache-Control` }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, RouteResult }
import akka.util.ByteString
import com.typesafe.config.Config
import expander.core.{ Expander, PathRequest }
import play.api.libs.json._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

object ExpanderFilter {

  def forConfig(config: Config, system: ActorSystem)(route: Route): Route = {
    import collection.JavaConversions._
    val passHeaders = config.getStringList("expander.pass-headers").toSeq
    val baseHost = Try(config.getString("expander.base-host")).toOption
    val resolves = config.getObject("expander.resolves")
    val resolvesMap = resolves.keySet().map(k ⇒ k → config.getString("expander.resolves." + k)).toMap

    val patterns: Seq[ResolvePattern] = ???

    apply(passHeaders, new JsonGenericProvider(patterns), system)(route)
  }

  private def hash(str: String) = MessageDigest.getInstance("MD5").digest(str.getBytes).map("%02x" format _).mkString

  def apply(passHeaders: Seq[String], expandContextProvider: JsValue ⇒ Map[JsPath, String], system: ActorSystem)(route: Route): Route = {
    val passHeadersLowerCase = passHeaders.map(_.toLowerCase).toSet

    parameter(Expander.Key.?) {
      case None ⇒
        route

      case Some(expandRequest) ⇒
        val reqs = PathRequest.parse(expandRequest)
        if (reqs.isEmpty) {
          route
        } else {
          extractRequestContext { reqCtx ⇒
            extractMaterializer { implicit mat ⇒
              extractExecutionContext { implicit ectx ⇒

                mapRouteResultFuture {
                  _.flatMap {
                    case RouteResult.Complete(resp) if resp.entity.contentType == ContentTypes.`application/json` ⇒

                      val headers = reqCtx.request.headers.filter(h ⇒ passHeadersLowerCase(h.lowercaseName()))
                      implicit lazy val expandContext = new JsonExpandContext(headers, expandContextProvider, system)

                      resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(bs ⇒ Json.parse(bs.decodeString("UTF-8"))).flatMap(Expander(_, reqs: _*)).flatMap { json ⇒
                        val jsonString = Json.stringify(json)

                        (get {
                          conditional(EntityTag(hash(jsonString))) {
                            mapResponseHeaders(_ :+ `Cache-Control`(`max-age`(0), `must-revalidate`)) {
                              complete(resp.status → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(jsonString)))
                            }
                          }
                        } ~ complete(resp.status → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(jsonString)))) (reqCtx)

                      }

                    case f ⇒
                      Future.successful(f)
                  }
                }(route)

              }

            }

          }

        }
    }
  }
}