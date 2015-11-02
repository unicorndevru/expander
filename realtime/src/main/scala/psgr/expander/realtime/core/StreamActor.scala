package psgr.expander.realtime.core

import akka.actor._
import play.api.libs.json.JsValue
import psgr.expander.core.MetaRef
import psgr.expander.realtime.StreamMediator

import scala.concurrent.duration._

class StreamActor(refs: Set[MetaRef], mediator: StreamMediator) extends Actor {

  import StreamActor._
  import context.dispatcher

  def createTimeout() = context.system.scheduler.scheduleOnce(20 seconds, self, Timeout)

  var watches = refs.map(_.href).map(mediator.watch(_, incoming))

  var messages = Vector.empty[JsValue]

  def incoming(v: JsValue) = {
    self ! v
  }

  override def receive = waiting(createTimeout())

  def waiting(timer: Cancellable): Receive = {
    case Join(n) ⇒
      timer.cancel()
      add(sender(), Set.empty, n)

    case Timeout ⇒
      context.stop(self)

    case m: JsValue ⇒
      messages = messages :+ m
  }

  def bound(to: Set[ActorRef]): Receive = {
    case Join(n) ⇒
      add(sender(), to, n)

    case Leave ⇒
      remove(sender(), to)
      context.unwatch(sender())

    case Terminated(t) ⇒
      remove(t, to)
      context.unwatch(t)

    case m: JsValue ⇒
      messages = messages :+ m
      val n = messages.size
      to.foreach(_ ! StreamEvent(n, m))
  }

  def add(actor: ActorRef, subscribers: Set[ActorRef], n: Int) = {
    context.become(bound(subscribers + actor))
    actor ! Ok
    context.watch(actor)
    sendMessages(actor, n)
  }

  def remove(actor: ActorRef, subscribers: Set[ActorRef]) = {
    val newTo = subscribers.filterNot(_ == actor)
    if (newTo.isEmpty) {
      context.become(waiting(createTimeout()))
    } else {
      context.become(bound(newTo))
    }
  }

  def sendMessages(actor: ActorRef, n: Int) = {
    messages.drop(n).zipWithIndex.foreach {
      case (v, i) ⇒
        actor ! StreamEvent(i + n, v)
    }
  }

  override def postStop() = {
    watches.foreach(_.cancel())
  }
}

object StreamActor {

  case class Join(n: Int = 0)

  case object Leave

  case object Timeout

  case object Ok

  def props(refs: Set[MetaRef], mediator: StreamMediator): Props = Props(new StreamActor(refs, mediator))
}