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
  def build(config: Config, httpResolve: ExpanderResolve)(implicit system: ActorSystem, mat: Materializer): ExpanderFilterConfig = {
    val forwardHeaders = config.getStringList("expander.forward-headers").toSet

    val conditionalEnabled = config.getBoolean("expander.enable-conditional")

    val patterns: Seq[ExpandPattern] = readPatterns(config)

    ExpanderFilterConfig(
      hrs ⇒ ec ⇒ new JsonExpandResolveContext(hrs, new JsonGenericProvider(patterns), httpResolve.resolver(ec))(mat, ec),
      forwardHeaders,
      conditionalEnabled = conditionalEnabled
    )
  }

  private val urlPatternR = ":([a-zA-Z]+)".r

  def readPatterns(config: Config): Seq[ExpandPattern] = {
    import PathRequest.parsePath
    config.getConfigList("expander.patterns").toSeq.flatMap { obj ⇒
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