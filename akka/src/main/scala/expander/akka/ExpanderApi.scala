package expander.akka

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import akka.stream.Materializer
import com.typesafe.config.Config
import expander.core.Expander
import expander.resolve.ExpanderResolve

class ExpanderApi(config: Config)(implicit system: ActorSystem, mat: Materializer) {

  private val port = config.getInt("expander.http.port")

  val logger = Logging(system, getClass)

  logger.info("starting expander http server on port: {}", port)

  val prefix = config.getString("expander.proxy.prefix")
  val filter = ExpanderFilter.forConfig(config)
  val resolve = ExpanderResolve.forConfig(config)

  implicit val exceptionHandler = ExceptionHandler{
    case e: Throwable ⇒
      logger.error(e, "Exception in expander filter app")
      throw e
  }

  val routes = Route.seal {
    encodeResponse {
      pathPrefix(prefix) {
        filter {
          extractExecutionContext { ec ⇒
            filter.extractExpandingHeaders { hrs ⇒
              extractRequest { req ⇒

                val r = resolve.resolver(ec)
                logger.debug("Expander proxying request: {}", req)

                complete(r(req.copy(
                  uri = req.uri.copy(rawQueryString = req.uri.rawQueryString.map(rqs ⇒
                    req.uri.query().filterNot(_._1 equalsIgnoreCase Expander.Key).toString())),
                  headers = req.headers.filter(filter.isPassHeader) ++ hrs
                )))
              }
            }
          }
        }
      }
    } ~ path("health") {
      // TODO: health endpoint
      complete("Operating")
    }
  }

  def run() = {
    Http(system).bindAndHandle(routes, config.getString("expander.http.interface"), port)
  }

}
