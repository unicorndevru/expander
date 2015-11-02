package psgr.expander.realtime

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

import akka.actor.Cancellable
import akka.event.{ EventBus, LookupClassification }
import com.google.inject.ImplementedBy
import play.api.libs.json.{ JsValue, Json, Writes }
import psgr.expander.core.MetaRef

import scala.util.Try

@ImplementedBy(classOf[SimpleAkkaStreamMediator])
trait StreamMediator {
  def push(ref: MetaRef, value: JsValue): Unit

  def push[T: Writes](value: T): Unit = {
    val json = Json.toJson(value)
    json.validate[MetaRef].foreach(push(_, json))
  }

  def watch(href: String, f: JsValue ⇒ Unit): Cancellable
}

@Singleton
class SimpleAkkaStreamMediator extends StreamMediator with EventBus with LookupClassification {
  type Event = (String, JsValue)
  type Classifier = String
  type Subscriber = JsValue ⇒ Unit

  class CancellableWatcher(subscriber: Subscriber, classifier: Classifier) extends Cancellable {

    private val cancelled: AtomicBoolean = new AtomicBoolean(false)

    override def cancel(): Boolean =
      if (!isCancelled &&
        unsubscribe(subscriber.asInstanceOf[Subscriber], classifier.asInstanceOf[Classifier])) {
        cancelled.set(true)
        true
      } else {
        false
      }

    override def isCancelled: Boolean = cancelled.get()
  }

  override protected def classify(event: Event): Classifier = event._1

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    Try(subscriber(event._2))
  }

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    (a.getClass.getName + a.hashCode()).compareTo(b.getClass.getName + b.hashCode())

  override protected def mapSize(): Int = 128

  override def push(ref: MetaRef, value: JsValue) = {
    publish(ref.href → value)
  }

  override def watch(href: String, f: (JsValue) ⇒ Unit) = {
    val w = new CancellableWatcher(f, href)
    subscribe(f, href)
    w
  }
}