package expander.resolve.consul

import akka.actor.{ Actor, ActorRef, Props, Status, Terminated }
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }
import play.api.libs.json.{ Json, Reads }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

trait ConsulKeyValueService {
  self: ConsulService ⇒

  import ConsulKeyValueService._

  private lazy val actorsWatcher = system.actorOf(Props(new ConsulKeyValueService.LockWatcher(this)), "consul-lock-watcher")

  def getValue(key: String): Future[Option[Value]] =
    get("/v1/kv/" + key)
      .flatMap {
        case resp if resp.status.isSuccess() ⇒
          resp.entity.dataBytes
            .runFold(ByteString(""))(_ ++ _).fast
            .map(bs ⇒ Json.parse(bs.utf8String).asOpt[Seq[Value]](Reads.seq(valueFmt)).getOrElse(Seq.empty).headOption)
        case _ ⇒
          Future.successful(Option.empty[Value])
      }

  def acquire(key: String, sesId: String): Future[Boolean] =
    if (!consulEnabled) Future.successful(true) else
      putEmpty(s"/v1/kv/$key?acquire=$sesId")
        .flatMap(resp ⇒
          resp.entity.dataBytes
            .runFold(ByteString(""))(_ ++ _)).fast.map(_.utf8String == "true")

  def acquireActor(key: String, sesId: String, actor: ⇒ ActorRef)(implicit timeout: Timeout = Timeout(1.second)): Future[ActorRef] =
    if (!consulEnabled) Future.successful(actor) else
      (actorsWatcher ? Acquire(key, sesId, () ⇒ actor)).mapTo[ActorRef]

  def release(key: String, sesId: String): Future[Boolean] =
    putEmpty(s"/v1/kv/$key?release=$sesId")
      .flatMap(resp ⇒
        resp.entity.dataBytes
          .runFold(ByteString(""))(_ ++ _)).fast.map(_.utf8String == "true")
}

object ConsulKeyValueService {

  case class Value(
    CreateIndex: Int,
    ModifyIndex: Int,
    LockIndex:   Int,
    Key:         String,
    Flags:       Int,
    Value:       String,
    Session:     Option[String]
  )

  private implicit val valueFmt = Json.format[Value]

  class LockWatcher(service: ConsulKeyValueService) extends Actor {

    def watching(actors: Map[(String, String), ActorRef], inverse: Map[ActorRef, Set[(String, String)]], pending: Map[(String, String), ActorRef]): Receive = {
      case Acquire(key, sesId, actor) ⇒
        val k = key → sesId
        actors.get(k) match {
          case Some(x) ⇒ sender() ! x
          case None ⇒
            val s = sender()
            service.acquire(key, sesId).onComplete {
              case Success(true) ⇒
                self ! Register(key, sesId, actor())
              case Success(false) ⇒
                self ! Fail(key, sesId, None)
              case Failure(e) ⇒
                self ! Fail(key, sesId, Some(e))
            }
            context.become(watching(actors, inverse, pending + (k → s)))
        }

      case Register(key, sesId, ref) ⇒
        val ks = (key, sesId)
        val s = pending(ks)
        context.watch(ref)
        s ! ref
        context.become(watching(actors + (ks → ref), inverse + (ref → (inverse.getOrElse(ref, Set.empty) + ks)), pending - ks))

      case Fail(key, sesId, err) ⇒
        val s = pending((key, sesId))
        s ! Status.Failure(err.getOrElse(new IllegalStateException("Cannot acquire")))
        context.become(watching(actors, inverse, pending - (key → sesId)))

      case Terminated(a) if inverse.contains(a) ⇒
        inverse(a).foreach((service.release _).tupled)
        context.become(watching(actors -- inverse(a), inverse - a, pending))
    }

    override def receive: Receive = watching(Map.empty, Map.empty, Map.empty)
  }

  case class Acquire(key: String, sesId: String, actor: () ⇒ ActorRef)

  case class Register(key: String, sesId: String, ref: ActorRef)

  case class Fail(key: String, sesId: String, err: Option[Throwable])

}