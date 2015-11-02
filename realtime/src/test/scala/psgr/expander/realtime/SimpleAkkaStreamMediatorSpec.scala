package psgr.expander.realtime

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{ JsString, JsValue }
import play.api.test.PlaySpecification
import psgr.expander.core.MetaRef

import scala.concurrent.Promise

class SimpleAkkaStreamMediatorSpec extends PlaySpecification {
  implicit val ee = ExecutionEnv.fromGlobalExecutionContext

  "simple stream mediator" should {
    "pub sub" in {
      val mediator: StreamMediator = new SimpleAkkaStreamMediator

      val p = Promise[JsValue]()

      val c = mediator.watch("/test", { v ⇒ p.success(v) })

      mediator.push(MetaRef("/test", Some("type")), JsString("hello world"))

      p.future must equalTo(JsString("hello world")).await

      c.cancel()

      val p1 = Promise[JsValue]()
      val p2 = Promise[JsValue]()
      val p3 = Promise[JsValue]()

      val c1 = mediator.watch("/p", { v ⇒ p1.success(v) })
      val c2 = mediator.watch("/p", { v ⇒ p2.success(v) })
      val c3 = mediator.watch("/test", { v ⇒ p3.success(v) })

      mediator.push(MetaRef("/p"), JsString("test p"))
      mediator.push(MetaRef("/test", Some("media test")), JsString("test test"))

      p1.future must equalTo(JsString("test p")).await
      p2.future must equalTo(JsString("test p")).await
      p3.future must equalTo(JsString("test test")).await

    }
  }

}
