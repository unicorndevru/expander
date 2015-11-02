package psgr.expander.realtime.core

import akka.actor.{ Actor, ActorRef, Props }
import play.api.libs.iteratee.Concurrent

class StreamFlowActor(n: Int) extends Actor {
  def receive = {
    case ch: Concurrent.Channel[StreamEvent] ⇒
      context.become(noStream(ch))

    case StreamFlowActor.Stop ⇒
      context.stop(self)
  }

  def noStream(ch: Concurrent.Channel[StreamEvent]): Receive = {
    case StreamActor.Ok ⇒
      context.become(active(sender(), ch))

    case StreamFlowActor.Stop ⇒
      context.stop(self)
  }

  def active(stream: ActorRef, ch: Concurrent.Channel[StreamEvent]): Receive = {
    case StreamFlowActor.Stop ⇒
      stream ! StreamActor.Leave
      context.stop(self)

    case item: StreamEvent ⇒
      ch.push(item)
  }
}

object StreamFlowActor {

  case object Stop

  def props(n: Int) = Props(new StreamFlowActor(n))
}