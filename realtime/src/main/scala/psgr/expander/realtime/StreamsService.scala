package psgr.expander.realtime

import javax.inject.Inject

import akka.actor._
import akka.pattern.ask
import com.google.inject.ImplementedBy
import play.api.libs.iteratee.{ Concurrent, Enumerator }
import psgr.expander.core.MetaRef
import psgr.expander.realtime.core._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

@ImplementedBy(classOf[SimpleAkkaStreamsService])
trait StreamsService {
  def start(refs: Set[MetaRef]): Future[String]

  def join(id: String)(implicit sender: ActorRef): Unit

  def flow(id: String, n: Int = 0): Future[Enumerator[StreamEvent]]
}

@javax.inject.Singleton
class SimpleAkkaStreamsService @Inject() (system: ActorSystem, mediator: StreamMediator) extends StreamsService {

  import StreamsGuardianActor._

  implicit val timeout = akka.util.Timeout(1 second)

  val guardian = system.actorOf(StreamsGuardianActor.props(mediator), "streams")

  def start(refs: Set[MetaRef]): Future[String] = {
    (guardian ? Create(refs)).mapTo[String]
  }

  def join(id: String)(implicit sender: ActorRef) = guardian.tell(Join(id), sender)

  def flow(id: String, n: Int = 0) = {
    (guardian ? Check(id)).mapTo[Boolean].flatMap {
      case true ⇒
        val flowActor = system.actorOf(StreamFlowActor.props(n))
        Future successful Concurrent.unicast[StreamEvent](
          onStart = { ch ⇒
          flowActor ! ch
          guardian.tell(Join(id, n), flowActor)
        },
          onError = { (s, i) ⇒
          flowActor ! StreamFlowActor.Stop
        },
          onComplete = {
          flowActor ! StreamFlowActor.Stop
        }

        )

      case false ⇒
        Future failed new NoSuchElementException("Stream not found for id: " + id)
    }
  }
}

