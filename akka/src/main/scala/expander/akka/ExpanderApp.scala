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
  implicit val mat = ActorMaterializer()

  val api = new ExpanderApi(config)

  api.run()

}
