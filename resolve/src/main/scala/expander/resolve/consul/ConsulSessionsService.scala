package expander.resolve.consul

import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import akka.util.ByteString
import play.api.libs.json.{ Json, Reads }

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait ConsulSessionsService {
  self: ConsulService ⇒

  import ConsulSessionsService._

  private val namedSessions = TrieMap.empty[String, String]

  def destroyAllKnownSessions() = Future.traverse(namedSessions.values)(destroySession)

  def destroySession(id: String): Future[HttpResponse] =
    putEmpty("/v1/session/destroy/" + id)

  def getSessionInfo(id: String): Future[Option[Session]] =
    getJson("/v1/session/info/" + id).map(_.asOpt[Seq[Session]](Reads.seq(sessionFmt)).getOrElse(Seq.empty).headOption)

  def createSession(flags: Int, name: String, ttl: Int = 20, checks: Set[String] = Set.empty, autoRenew: Boolean = true): Future[String] =
    if (!consulEnabled) Future.successful("") else
      namedSessions.get(name) match {
        case Some(id) ⇒
          Future.successful(id)

        case None ⇒
          http.singleRequest(HttpRequest(
            method = HttpMethods.PUT,
            uri = baseAddr + "/v1/session/create",
            entity = HttpEntity.apply(ContentTypes.`application/json`, Json.stringify(Json.obj(
              "Name" → name,
              "Checks" → (checks + "serfHealth"),
              "Flags" → flags,
              "Behavior" → "delete",
              "TTL" → s"${ttl}s"
            )))
          )).flatMap(resp ⇒
            resp.entity.dataBytes
              .runFold(ByteString(""))(_ ++ _)).fast.map { r ⇒
            val id = (Json.parse(r.utf8String) \ "ID").as[String]
            namedSessions(name) = id

            if (autoRenew) {
              val period = (ttl / 2).seconds

              def renew(): Unit = renewSession(id)
                .foreach {
                  r ⇒
                    if (r && namedSessions.get(name).contains(id)) {
                      system.scheduler.scheduleOnce(period)(renew())
                    } else {
                      namedSessions.remove(name, id)
                    }
                }

              system
                .scheduler
                .scheduleOnce(period)(renew())
            }
            id
          }
      }

  def renewSession(id: String): Future[Boolean] =
    putEmpty("/v1/session/renew/" + id)
      .map(_.status.isSuccess())

}

object ConsulSessionsService {

  case class Session(
    LockDelay:   Long,
    Checks:      Set[String],
    Node:        String,
    ID:          String,
    CreateIndex: Int,
    Name:        Option[String]
  )

  implicit val sessionFmt = Json.format[Session]

}