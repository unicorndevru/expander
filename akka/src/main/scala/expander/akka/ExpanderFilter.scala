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

object ExpanderFilter {

  def forConfig(config: Config, system: ActorSystem)(route: Route): Route =
    apply(ExpanderFilterConfig.build(config, system))(route)

  private def hash(str: String) = MessageDigest.getInstance("MD5").digest(str.getBytes).map("%02x" format _).mkString

  def apply(conf: ExpanderFilterConfig)(route: Route): Route = {
    import conf._
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
                      implicit lazy val expandContext = expandContextProvider(headers)(mat, ectx)

                      resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(bs ⇒ Json.parse(bs.decodeString("UTF-8"))).flatMap(Expander(_, reqs: _*)).flatMap { json ⇒
                        val jsonString = Json.stringify(json)

                        val completeJson = complete(resp.status → HttpEntity.Strict(ContentTypes.`application/json`, ByteString(jsonString)))

                        if (conditionalEnabled) {
                          (get {
                            conditional(EntityTag(hash(jsonString))) {
                              mapResponseHeaders(_ :+ `Cache-Control`(`max-age`(0), `must-revalidate`)) {
                                completeJson
                              }
                            }
                          } ~ completeJson) (reqCtx)
                        } else completeJson(reqCtx)

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