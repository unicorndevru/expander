package expander.resolve.consul

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import akka.stream.Materializer
import akka.util.ByteString
import play.api.libs.json.{ JsValue, Json }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.Try

class ConsulService(
  val consulEnabled: Boolean,
  val baseAddr:      String,
  val dnsEnabled:    Boolean

)(implicit val system: ActorSystem, val mat: Materializer)

    extends ConsulKeyValueService with ConsulSessionsService {

  val http = Http(system)

  protected def get(url: String): Future[HttpResponse] =
    http.singleRequest(HttpRequest(uri = baseAddr + url))

  protected def getJson(url: String): Future[JsValue] =
    http.singleRequest(HttpRequest(uri = baseAddr + url))
      .flatMap(resp ⇒
        resp.entity.dataBytes
          .runFold(ByteString(""))(_ ++ _).fast
          .map(bs ⇒ Json.parse(bs.utf8String)))

  protected def putEmpty(url: String): Future[HttpResponse] =
    http.singleRequest(HttpRequest(
      method = HttpMethods.PUT,
      uri = s"$baseAddr$url"
    ))

  case class Node(
    Node: NodeInfo
  )

  case class NodeInfo(
    Node:            String,
    Address:         String,
    TaggedAddresses: Map[String, String]
  )

  implicit val nodeNodeFmt = Json.format[NodeInfo]
  implicit val nodeFmt = Json.format[Node]

  system.registerOnTermination(new Runnable {
    override def run(): Unit =
      Try(
        Await.result(
          destroyAllKnownSessions(), 5.seconds
        )
      )
  })

  def getNode(node: String): Future[Node] =
    getJson("/v1/catalog/node/" + node).map(_.as[Node])

}
