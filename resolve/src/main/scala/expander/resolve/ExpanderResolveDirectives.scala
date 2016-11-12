package expander.resolve

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.util.{ Failure, Success }

class ExpanderResolveDirectives(er: ExpanderResolve, sessionsTtl: Int = 20) {

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
    extractExecutionContext.flatMap { implicit ctx ⇒
      onSuccess(er.consul.createSession(flags, name, sessionsTtl, checks)).flatMap { s ⇒
        provide(s)
      }
    }

  private def extractSessionAndKey(flags: Int, name: String, checks: Set[String] = Set.empty): Directive[(String, String)] =
    if (!er.consul.consulEnabled) tprovide(("", "")) else
      extractUri.flatMap { uri ⇒
        er.extractKey(uri) match {
          case Some(key) ⇒
            withSessionId(flags, name, checks).flatMap { sesId ⇒
              onSuccess(er.consul.acquire(key, sesId)).flatMap {
                case true ⇒
                  tprovide(sesId, key)
                case false ⇒
                  StandardRoute(proxy)
              }
            }
          case None ⇒
            failWith(new IllegalArgumentException("No key provided for acquire"))
        }
      }

  def acquireOrProxy(flags: Int, name: String, checks: Set[String] = Set.empty): Directive0 =
    extractSessionAndKey(flags, name, checks).tflatMap(_ ⇒ pass)

  def actorOrProxy(flags: Int, name: String, actor: ⇒ ActorRef, checks: Set[String] = Set.empty): Directive1[ActorRef] =
    extractSessionAndKey(flags, name, checks).tflatMap {
      case (sesId, key) ⇒
        onComplete(er.consul.acquireActor(key, sesId, actor)).flatMap {
          case Success(ref) ⇒ provide(ref)
          case Failure(_)   ⇒ StandardRoute(proxy)
        }
    }

}
