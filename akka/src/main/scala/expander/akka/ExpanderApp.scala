package expander.akka

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import expander.resolve.ExpanderResolve

import scala.util.Try

object ExpanderApp extends App {
  val config = ConfigFactory.load()

  implicit val system = ActorSystem("expander")
  implicit val executor = system.dispatcher
  implicit val mat = ActorMaterializer()

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
              println(Console.RED + req + Console.RESET)
              complete(r(req))
            }
          }
        }
      }
    }
    // TODO: health endpoint
  }

  Http(system).bindAndHandle(routes, config.getString("expander.http.interface"), port)

}
