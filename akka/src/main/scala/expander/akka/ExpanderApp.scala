package expander.akka

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

object ExpanderApp extends App {
  val config = ConfigFactory.load()

  implicit val system = ActorSystem("expander")
  implicit val mat = ActorMaterializer()

  val api = new ExpanderApi(config)

  api.run()

}
