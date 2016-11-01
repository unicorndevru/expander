package expander.resolve

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Source

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

class ExpanderResolveDirectives(er: ExpanderResolve, sessionsTtl: Int = 20) {

  private val sessionIds = TrieMap.empty[String, String]

  val proxy: Route =
    extractExecutionContext { implicit ctx ⇒
      extractRequest { req ⇒
        onSuccess(er.process(req)) { vv ⇒
          extractMaterializer { implicit mat ⇒
            complete(er.http.singleRequest(vv))
          }
        }
      }
    }

  def withSessionId(flags: Int, name: String, checks: Set[String] = Set.empty): Directive1[String] =
    sessionIds.get(name) match {
      case Some(s) ⇒ provide(s)
      case None ⇒
        extractExecutionContext.flatMap { implicit ctx ⇒
          onSuccess(er.consul.createSession(flags, name, sessionsTtl, checks)).flatMap { s ⇒
            extractLog.flatMap { log ⇒
              extractMaterializer.flatMap { implicit mat ⇒

                Source
                  .empty[Unit]
                  .keepAlive((sessionsTtl - 3).seconds, () ⇒ ())
                  .runForeach(_ ⇒ er.consul.renewSession(s).foreach(_ ⇒ log.debug("Pinged: " + name + " id=" + s)))

                sessionIds(name) = s
                provide(s)
              }
            }
          }
        }
    }

  def acquireOrProxy(flags: Int, name: String, checks: Set[String] = Set.empty)(route: Route): Route = {
    extractUri { uri ⇒
      er.extractKey(uri) match {
        case Some(key) ⇒
          withSessionId(flags, name, checks) { sesId ⇒
            extractExecutionContext { implicit ctx ⇒
              onSuccess(er.consul.acquire(key, sesId)) {
                case true ⇒
                  route
                case false ⇒
                  proxy
              }
            }
          }
        case None ⇒
          failWith(new IllegalArgumentException("No key provided for acquire"))
      }
    }
  }

}
