package expander.resolve

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.stream.Materializer
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scala.util.matching.Regex

class ExpanderResolve(
    val consul:   Consul,
    val patterns: Seq[ExpanderResolve.Pattern]
)(implicit system: ActorSystem, mat: Materializer) {

  val http = Http(system)

  def resolver(implicit ctx: ExecutionContext): HttpRequest ⇒ Future[HttpResponse] =
    req ⇒ process(req).flatMap(r ⇒ http.singleRequest(r))

  def extractKey(uri: Uri): Option[String] = {
    val path = uri.path.toString()
    patterns.find(_.path.pattern.matcher(path).matches())
      .flatMap(p ⇒ p.consulKey.map(p.path.pattern.matcher(path).replaceAll))
  }

  // aim is to build mapper uri -> uri, and then req -> req, so req -> f[resp]
  // by default, mapper should act as identity, but with overrides based either on host and url
  // it should return appropriate host, if available
  // so it's about host substitution
  def substituteUri(uri: Uri)(implicit ctx: ExecutionContext): Future[Uri] = {
    val path = uri.path.toString()

    // Find URI correction pattern
    patterns
      .find(_.path.pattern.matcher(path).matches())
      .fold(Future.successful(uri)){ p ⇒
        // Pattern is found
        // Make matcher. It's used for path-modify substitutions as well as for consul key computation
        val r = p.path.pattern.matcher(path)

        // Substitute what we can
        val uriRes = uri.copy(
          scheme = "http",
          authority = uri.authority.copy(
            host = p.host.map(Uri.Host(_)).getOrElse(uri.authority.host),
            port = if (p.port > 0) p.port else uri.authority.port
          ),
          path = p.modify.map(r.replaceAll).map(Uri.Path(_)).getOrElse(uri.path)
        )

        // Prepare fallback result
        val result = Future.successful(uriRes)

        p.consulKey.map(r.replaceAll).fold(result) { key ⇒
          consul.getValue(key).flatMap(_.headOption.fold(result) { v ⇒
            // Is lock acquired and session provided?
            v.Session.fold(result) { ses ⇒
              // Get session
              consul.getSessionInfo(ses).flatMap(_.headOption.fold(result) { s ⇒
                (if (consul.dnsEnabled) {
                  // If DNS enabled, we provide node dns address to be resolved via lookup
                  Future.successful(s.Node + ".node.consul")
                } else {
                  // If DNS disabled, we lookup the node with HTTP API call
                  consul.getNode(s.Node).map(_.Node.Address)
                }).map(host ⇒ uriRes.copy(authority = uriRes.authority.copy(
                  host = Uri.Host(host),
                  port = if (v.Flags > 0) v.Flags else uriRes.authority.port
                )))
              })
            }
          })
        }
      }
  }

  def process(req: HttpRequest)(implicit ctx: ExecutionContext): Future[HttpRequest] = {
    substituteUri(req.uri).map(uri ⇒ req.copy(
      uri = uri,
      headers = req.headers
      .filter(_.renderInRequests()).filterNot(h ⇒ h.is("host") || h.is("user-agent"))
      :+ `User-Agent`("expander-resolve")
    ))
  }

}

object ExpanderResolve {
  case class Pattern(
    path:      Regex,
    consulKey: Option[String],
    modify:    Option[String],
    host:      Option[String],
    port:      Int
  )

  def forConfig(config: Config)(implicit system: ActorSystem, mat: Materializer) = {
    import scala.collection.convert.wrapAsScala._

    new ExpanderResolve(
      consul = new Consul(
      Try(config.getString("expander.resolve.consul-addr")).getOrElse("http://127.0.0.1:8500"),
      Try(config.getBoolean("expander.resolve.consul-dns-enabled")).getOrElse(false)
    ),
      patterns = Try(
        config.getConfigList("expander.resolve.patterns").toSeq
      ).getOrElse(Seq(ConfigFactory.parseString(
        """
          | path = ".*"
          | host = "localhost"
        """.stripMargin
      )))
        .map(cfg ⇒
          Pattern(
            path = cfg.getString("path").r,
            consulKey = Try(cfg.getString("consul-key")).toOption,
            modify = Try(cfg.getString("modify-path")).toOption,
            host = Try(cfg.getString("host")).toOption,
            port = Try(cfg.getInt("port")).getOrElse(0)
          ))
    )
  }
}