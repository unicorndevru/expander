package psgr.expander.realtime

import akka.actor.ActorSystem
import org.specs2.concurrent.ExecutionEnv
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsString
import play.api.test.PlaySpecification
import psgr.expander.core.MetaRef
import psgr.expander.realtime.core.StreamEvent

class StreamsServiceSpec extends PlaySpecification {
  implicit val ee = ExecutionEnv.fromGlobalExecutionContext

  "streams service" should {
    "work" in {

      val system = ActorSystem("test")

      val mediator: StreamMediator = new SimpleAkkaStreamMediator

      val streamsService: StreamsService = new SimpleAkkaStreamsService(system, mediator)

      streamsService.start(Set(MetaRef("/test/1"), MetaRef("/test/2", Some("test media")))) must beLike[String] {
        case id ⇒

          val flow = streamsService.flow(id)

          mediator.push(MetaRef("/test/1"), JsString("test 1"))
          mediator.push(MetaRef("/test"), JsString("test  unexistent"))
          mediator.push(MetaRef("/test/2"), JsString("test 2"))

          (flow flatMap (_ run Iteratee.takeUpTo(2))) must beEqualTo(StreamEvent(0, JsString("test 1")) :: StreamEvent(1, JsString("test 2")) :: Nil).await

          (streamsService.flow(id, 1) flatMap (_ run Iteratee.head)) must beSome(StreamEvent(1, JsString("test 2"))).await

      }.await

    }
  }
}
