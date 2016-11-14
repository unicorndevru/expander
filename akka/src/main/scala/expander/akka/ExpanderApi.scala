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
import expander.resolve.consul.ConsulService

import scala.concurrent.Await
import scala.concurrent.duration._

class ExpanderApi(config: Config)(implicit system: ActorSystem, mat: Materializer) {

  private val port = config.getInt("expander.http.port")

  val logger = Logging(system, getClass)

  logger.info("starting expander http server on port: {}", port)

  val prefix = config.getString("expander.proxy.prefix")
  val consul = ConsulService.build(config)
  val resolve = ExpanderResolve.build(config, consul)
  val filter = ExpanderFilter.build(config, resolve)

  implicit val exceptionHandler = ExceptionHandler{
    case e: Throwable ⇒
      logger.error(e, "Exception in expander filter app")
      throw e
  }

  val routes = Route.seal {
    encodeResponse {
      pathPrefix(prefix) {
        filter {
          extractExecutionContext { implicit ec ⇒
            filter.extractExpandingHeaders { hrs ⇒
              extractRequest { req ⇒

                val r = resolve.resolver(ec)
                logger.debug("Expander proxying request: {}", req)

                complete(r(req.copy(
                  uri = req.uri.copy(rawQueryString = req.uri.rawQueryString.map(rqs ⇒
                    req.uri.query().filterNot(_._1 equalsIgnoreCase Expander.Key).toString())),
                  headers = req.headers.filter(filter.isPassHeader) ++ hrs
                )).recover {
                  case e: Throwable ⇒
                    print(e)
                    e.printStackTrace()
                    throw e
                })
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
    import scala.concurrent.ExecutionContext.Implicits.global

    val iface = config.getString("expander.http.interface")

    Http(system)
      .bindAndHandle(routes, iface, port)
      .flatMap(_ ⇒
        consul.registerService(
          name = "expander",
          port = port,
          httpCheck = Some(s"http://${if (iface.contains("0.0.0.0")) "127.0.0.1" else iface}:$port/health")
        )).map {
        case true ⇒
          logger.info("Registering on termination...")
          system.registerOnTermination(
            Await.result(consul.deregisterService("expander").map(v ⇒ logger.info("Deregistered? {}", v)), 3.seconds)
          )
          true
        case false ⇒
          false
      }
  }

}
