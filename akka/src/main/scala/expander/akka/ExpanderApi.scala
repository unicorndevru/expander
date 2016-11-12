package expander.akka

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.config.Config
import expander.resolve.ExpanderResolve

import scala.util.Try

class ExpanderApi(config: Config)(implicit system: ActorSystem, mat: Materializer) {

  private val port = config.getInt("expander.http.port")

  val logger = Logging(system, getClass)

  logger.info("starting expander http server on port: {}", port)

  val prefix = Try(config.getString("expander.proxy.prefix")).getOrElse("api")
  val filter = ExpanderFilter.forConfig(config)
  val resolve = ExpanderResolve.forConfig(config)

  val routes = Route.seal {
    encodeResponse {
      pathPrefix(prefix) {
        filter {
          extractExecutionContext { ec ⇒
            val r = resolve.resolver(ec)
            extractRequest { req ⇒
              logger.debug("Expander proxying request: {}", req)
              complete(r(req))
            }
          }
        }
      }
    }
    // TODO: health endpoint
  }

  def run() = {
    Http(system).bindAndHandle(routes, config.getString("expander.http.interface"), port)
  }

}
