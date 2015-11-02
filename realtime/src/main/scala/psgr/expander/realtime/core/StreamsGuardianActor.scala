package psgr.expander.realtime.core

import java.util.UUID

import akka.actor.{ Status, Actor, Props }
import psgr.expander.core.MetaRef
import psgr.expander.realtime.StreamMediator

object StreamsGuardianActor {

  case class Create(refs: Set[MetaRef])

  case class Join(id: String, n: Int = 0)

  case class Check(id: String)

  def props(mediator: StreamMediator) = Props(new StreamsGuardianActor(mediator))
}

class StreamsGuardianActor(mediator: StreamMediator) extends Actor {

  import StreamsGuardianActor._

  override def receive = {
    case Create(refs) ⇒
      val id = UUID.randomUUID().toString
      context.actorOf(StreamActor.props(refs, mediator), id)
      sender() ! id

    case Join(id, n) ⇒
      context.child(id) match {
        case Some(c) ⇒
          c.tell(StreamActor.Join(n), sender())
        case None ⇒
          sender() ! Status.Failure(new NoSuchElementException("Stream not found for id: " + id))
      }

    case Check(id) ⇒
      sender() ! context.child(id).isDefined
  }
}