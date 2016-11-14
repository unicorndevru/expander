package expander.resolve.consul

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.config.Config
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

  case class Check(
    DeregisterCriticalServiceAfter: Option[String],
    Script:                         Option[String],
    HTTP:                           String,
    Interval:                       Option[String]
  )

  case class ServiceNode(
    Node:           String,
    Address:        String,
    ServiceID:      String,
    ServiceName:    String,
    ServiceTags:    JsValue,
    ServiceAddress: String,
    ServicePort:    Int
  )

  case class ServiceRegister(
    Name:  String,
    Port:  Int,
    Tags:  Set[String],
    Check: Option[Check]
  )

  implicit val nodeNodeFmt = Json.format[NodeInfo]
  implicit val nodeFmt = Json.format[Node]
  implicit val serviceNodeFmt = Json.format[ServiceNode]

  implicit val checkFmt = Json.format[Check]
  implicit val serviceRegisterFmt = Json.format[ServiceRegister]

  system.registerOnTermination(Try(
    Await.result(
      destroyAllKnownSessions(), 3.seconds
    )
  ))

  def getNode(node: String): Future[Node] =
    getJson("/v1/catalog/node/" + node).map(_.as[Node])

  def getService(service: String): Future[Seq[ServiceNode]] =
    getJson("/v1/catalog/service/" + service).map(_.as[Seq[ServiceNode]])

  def registerService(
    name:            String,
    port:            Int,
    tags:            Set[String]    = Set.empty,
    httpCheck:       Option[String] = None,
    deregisterAfter: Duration       = Duration.Undefined,
    interval:        Duration       = 10.seconds
  ): Future[Boolean] =
    http.singleRequest(HttpRequest(
      method = HttpMethods.PUT,
      uri = s"$baseAddr/v1/agent/service/register",
      entity = HttpEntity(ContentTypes.`application/json`, Json.stringify(Json.toJson(ServiceRegister(
        Name = name,
        Port = port,
        Tags = tags,
        Check = httpCheck.map(c ⇒
          Check(
            DeregisterCriticalServiceAfter = Some(deregisterAfter).filter(_.isFinite()).map(_.toMinutes + "m"),
            Script = None,
            HTTP = c,
            Interval = Some(interval).filter(_.isFinite()).map(_.toSeconds + "s")
          ))
      ))))
    )).map(_.status.isSuccess())

  def deregisterService(name: String): Future[Boolean] =
    putEmpty("/v1/agent/service/deregister/" + name).map(_.status.isSuccess())
}

object ConsulService {
  def build(config: Config)(implicit system: ActorSystem, mat: Materializer) = new ConsulService(
    config.getBoolean("expander.resolve.consul.enabled"),
    config.getString("expander.resolve.consul.addr"),
    config.getBoolean("expander.resolve.consul.dns-enabled")
  )
}