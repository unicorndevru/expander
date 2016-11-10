package expander.resolve

import akka.actor.{ Actor, ActorRef, Props, Terminated }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.collection.concurrent.TrieMap

class ExpanderResolveDirectives(er: ExpanderResolve, sessionsTtl: Int = 20) {

  private val resourceActors = TrieMap.empty[String, ActorRef]

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

  def acquireOrProxyP(flags: Int, name: String, checks: Set[String] = Set.empty): Directive[(String, String)] =
    extractUri.flatMap { uri ⇒
      er.extractKey(uri) match {
        case Some(key) ⇒
          withSessionId(flags, name, checks).flatMap { sesId ⇒
            extractExecutionContext.flatMap { implicit ctx ⇒
              onSuccess(er.consul.acquire(key, sesId)).flatMap {
                case true ⇒
                  tprovide(sesId, key)
                case false ⇒
                  StandardRoute(proxy)
              }
            }
          }
        case None ⇒
          failWith(new IllegalArgumentException("No key provided for acquire"))
      }
    }

  def acquireOrProxy(flags: Int, name: String, checks: Set[String] = Set.empty): Directive0 =
    acquireOrProxyP(flags, name, checks).tflatMap(_ ⇒ pass)

  def actorOrProxy(flags: Int, name: String, actor: ⇒ ActorRef, checks: Set[String] = Set.empty): Directive1[ActorRef] =
    acquireOrProxyP(flags, name, checks).tflatMap {
      case (sesId, key) ⇒
        val actorName = name + ":" + key

        resourceActors.get(actorName) match {
          case Some(a) ⇒ provide(a)

          case None ⇒
            val a = resourceActors
              .putIfAbsent(actorName, actor)
              .orElse(resourceActors.get(actorName))
              .getOrElse(throw new IllegalStateException(s"Can't create an actor for $actorName"))

            extractActorSystem.flatMap { system ⇒
              system.actorOf(Props(new Actor {
                context.watch(a)

                override def receive: Receive = {
                  case Terminated(_) ⇒
                    import context.dispatcher

                    resourceActors.remove(actorName, a)
                    er.consul.release(key, sesId)
                }

              }))

              provide(a)
            }
        }
    }

}
