package expander.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CustomHeader
import akka.stream.Materializer
import com.typesafe.config.Config
import expander.core.{ ExpandContext, PathRequest }
import expander.resolve.ExpanderResolve
import play.api.libs.json.JsValue

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.util.Try

case class ExpanderFilterConfig(
  expandContextProvider: collection.immutable.Seq[HttpHeader] ⇒ ExecutionContext ⇒ ExpandContext[JsValue],
  forwardHeaders:        Set[String],
  conditionalEnabled:    Boolean
)

object ExpanderFilterConfig {
  def build(config: Config)(implicit system: ActorSystem, mat: Materializer): ExpanderFilterConfig = {

    val httpResolve = ExpanderResolve.forConfig(config)

    val forwardHeaders = Try(config.getStringList("expander.forward-headers").toSet).getOrElse(Set.empty)
    val setHeadersConfOpt = Try(config.getObject("expander.set-headers")).toOption

    val conditionalEnabled = Try(config.getBoolean("expander.enable-conditional")).getOrElse(false)

    val setHeaders: Seq[HttpHeader] = setHeadersConfOpt.fold(Seq.empty[HttpHeader]) { setHeadersConf ⇒
      setHeadersConf.keySet().map { k ⇒ k → Try(config.getString("expander.set-headers." + k)).toOption }.collect {
        case (k, Some(v)) ⇒ new CustomHeader {
          override def name() = k

          override def value() = v

          override def renderInResponses() = false

          override def renderInRequests() = true
        }
      }.toSeq
    }
    val patterns: Seq[ExpandPattern] = readPatterns(config)

    ExpanderFilterConfig(
      hrs ⇒ ec ⇒ new JsonExpandResolveContext(hrs ++ setHeaders, new JsonGenericProvider(patterns), httpResolve.resolver(ec))(mat, ec),
      forwardHeaders,
      conditionalEnabled = conditionalEnabled
    )
  }

  private val urlPatternR = ":([a-zA-Z]+)".r

  def readPatterns(config: Config): Seq[ExpandPattern] = {
    import PathRequest.parsePath
    Try(config.getConfigList("expander.patterns").toSeq).getOrElse(Seq.empty).flatMap { obj ⇒
      for {
        url ← Try(obj.getString("url")).toOption

        urlKeys = urlPatternR.findAllMatchIn(url).map(m ⇒ m.group(1)).toSet

        path ← Try(obj.getString("path")).toOption.flatMap(parsePath)

        requiredBase = Try(obj.getObject("required")).map(_.keySet().flatMap { k ⇒
          Try(obj.getString("required." + k)).toOption.flatMap(parsePath).map(k → _)
        }.toMap).getOrElse(Map.empty)

        requiredUrl = urlKeys.filterNot(requiredBase.contains).flatMap(k ⇒ parsePath(k).map(k → _))

        required = requiredBase ++ requiredUrl

        optional = Try(obj.getObject("optional")).map(_.keySet().flatMap { k ⇒
          Try(obj.getString("optional." + k)).toOption.flatMap(parsePath).map(k → _)
        }.toMap).getOrElse(Map.empty)

      } yield ExpandPattern(
        url,
        path,
        urlKeys,
        required,
        optional
      )

    }
  }
}