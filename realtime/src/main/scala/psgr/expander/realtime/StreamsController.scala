package psgr.expander.realtime

import javax.inject.Inject

import play.api.libs.EventSource
import play.api.libs.iteratee.Concurrent
import play.api.mvc.{ Action, Controller }
import psgr.expander.realtime.core.StreamEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class StreamsController @Inject() (streamService: StreamsService) extends Controller {

  implicit object dataExtractor extends EventSource.EventDataExtractor[StreamEvent](_.value.toString())

  implicit object nameExtractor extends EventSource.EventNameExtractor[StreamEvent](_ ⇒ None)

  implicit object idExtractor extends EventSource.EventIdExtractor[StreamEvent](e ⇒ Some(e.id.toString))

  def events(id: String) = Action.async {
    implicit rh ⇒
      for {
        flow ← streamService.flow(id, rh.headers.get("Last-Event-ID").flatMap(v ⇒ Try(v.toInt).toOption).getOrElse(0))
      } yield Ok.feed(flow
        &> Concurrent.buffer(22)
        &> EventSource()).as("text/event-stream")
  }

}
