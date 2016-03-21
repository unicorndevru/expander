package expander.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CustomHeader
import akka.stream.Materializer
import com.typesafe.config.Config
import expander.core.{ ExpandContext, PathRequest }
import play.api.libs.json.JsValue

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.util.Try

case class ExpanderFilterConfig(
  expandContextProvider: collection.immutable.Seq[HttpHeader] ⇒ (Materializer, ExecutionContext) ⇒ ExpandContext[JsValue],
  passHeaders:           Set[String],
  conditionalEnabled:    Boolean
)

object ExpanderFilterConfig {
  def build(config: Config, system: ActorSystem): ExpanderFilterConfig = {

    val passHeaders = config.getStringList("expander.pass-headers").toSet
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
    val patterns: Seq[ResolvePattern] = readPatterns(config)

    ExpanderFilterConfig(
      hrs ⇒ new JsonExpandResolveContext(hrs ++ setHeaders, new JsonGenericProvider(patterns), system)(_, _),
      passHeaders,
      conditionalEnabled = conditionalEnabled
    )
  }

  private val urlPatternR = ":([a-zA-Z]+)".r

  def readPatterns(config: Config): Seq[ResolvePattern] = {
    import PathRequest.parsePath
    val baseUrl = Try(config.getString("expander.base-url")).getOrElse("")
    Try(config.getConfigList("expander.patterns").toSeq).getOrElse(Seq.empty).flatMap { obj ⇒
      for {
        url ← Try(baseUrl + obj.getString("url")).toOption

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

      } yield ResolvePattern(
        url,
        path,
        urlKeys,
        required,
        optional
      )

    }
  }
}