package psgr.expander.play

import java.net.URLDecoder
import javax.inject.Inject

import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{ Configuration, Logger }
import psgr.expander.core._
import psgr.expander.protocol.{ MediaType, MetaUrlParams }
import psgr.failures.JsonApiFailure

import scala.collection.concurrent.TrieMap
import scala.collection.convert.decorateAsScala._
import scala.concurrent.Future

class MetaResolver @Inject() (ws: WSClient, config: Configuration, metaInterceptor: MetaResolveInterceptor) {

  val hosts = config.getConfigList("psgr.expander.resolve").map { confList ⇒
    confList.asScala.map {
      conf ⇒
        conf.getString("prefix") → conf.getString("substitute")
    }.collect {
      case (Some(k), Some(v)) ⇒ k → v
    }.toMap
  }.getOrElse(Map.empty[String, String])

  val passRequestHeaders: Seq[String] = config.getStringSeq("psgr.expander.pass-headers").getOrElse(Seq.empty)
  /**
   * Warning: response headers are passed only when we got response (when expanding in filter)
   */
  val passResponseHeaders: Seq[String] = config.getStringSeq("psgr.expander.pass-response-headers").getOrElse(Seq.empty)
  val requestTimeout = config.getMilliseconds("psgr.expander.timeout").getOrElse(5000l)

  val unknownPrefixFailure: FieldResolveResult = FieldResolveFailure(Json.toJson(JsonApiFailure(500, "unknown_prefix", "Meta href prefix is unknown and cannot be expanded", "expander")).asInstanceOf[JsObject])
  val cannotExpandFailure: FieldResolveResult = FieldResolveFailure(Json.toJson(JsonApiFailure(500, "cannot_expand", "Field cannot be expanded for an unknown reason.", "expander")).asInstanceOf[JsObject])

  def cannotExpandFailureCode(code: Int): FieldResolveResult = FieldResolveFailure(Json.toJson(JsonApiFailure(code, "cannot_expand", "Field cannot be expanded for an unknown reason.", "expander")).asInstanceOf[JsObject])

  val expandedHeader = config.getString("psgr.expander.expanded-header").getOrElse("X-Expanded")

  val log = Logger("expander")

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getFields(rh: RequestHeader): Field =
    Field.read(rh.queryString.get("expand").fold[List[String]](Nil)(_.toList) ++ rh.headers.get("Accept").flatMap(MediaType.read).flatMap(_.params.find(_._1 == "expand").map(_._2).map(URLDecoder.decode(_, "UTF-8"))): _*)

  def apply(implicit rh: RequestHeader): JsonResolver =

    new JsonResolver {
      val cache = TrieMap[String, Future[FieldResolveResult]]()

      val passHeaders = passRequestHeaders.map(h ⇒ rh.headers.get(h).map(h → _)).collect { case Some(kv) ⇒ kv }

      log.trace(s"Pass = $passHeaders")

      override def apply(ref: MetaRef) = apply(
        ref,
        ref.mediaType.flatMap(MediaType.read).flatMap(_.params.find(_._1 == "expand").map(_._2).map(URLDecoder.decode(_, "UTF-8"))).map(_.mkString(",")).map(Field.read(_)).getOrElse(Field.empty)
      )

      override def apply(ref: MetaRef, field: Field) = {
        hosts.find(kv ⇒ ref.href.startsWith(kv._1)).fold(Future.successful(unknownPrefixFailure)) {
          case (prefix, substitute) ⇒
            val url = substitute + ref.href.drop(prefix.length)
            cache.getOrElseUpdate(url, {
              log.trace("Going to expand new url: " + url)

              val req = ws.url(substitute + ref.href.drop(prefix.length)).withHeaders(passHeaders: _*).withQueryString(field.query.toSeq: _*)
              val fullReq = ref.mediaType.fold(req)(v ⇒ req.withHeaders("Accept" → v))

              fullReq.withRequestTimeout(requestTimeout).get().map { resp ⇒
                log.trace(s"Got for $url: " + resp)
                try {
                  FieldResolveSuccess(ref, resp.json.as[JsObject])
                } catch {
                  case e: Throwable ⇒
                    resp.status match {
                      case s if s >= 200 && s < 400 ⇒
                        cannotExpandFailure
                      case s ⇒
                        cannotExpandFailureCode(s)
                    }
                }
              }
            }.recover {
              case e: Throwable ⇒
                log.error("Uncaught / not preprocessed exception", e)
                cannotExpandFailure
            }.map {
              case res @ FieldResolveSuccess(_, jo) if field.nonEmpty ⇒
                jo.value.getOrElse("meta", Json.toJson(ref).asInstanceOf[JsObject]) match {
                  case mo: JsObject ⇒
                    mo.value.get("mediaType") match {
                      case Some(JsString(t)) ⇒
                        val mt = t + MetaUrlParams.encode(if (t.contains('&') || t.contains(';')) "&" else ";")(Seq("expand" → field.mkString(true)))

                        res.copy(value = jo deepMerge Json.obj("meta" → (mo ++ Json.obj("mediaType" → mt))))
                      case _ ⇒
                        res
                    }

                  case _ ⇒ res
                }
              case res ⇒
                res
            })
        }
      }
    }

  def resolve(href: String)(implicit rh: RequestHeader): Future[JsObject] = apply(rh).apply(MetaRef(href = href)).flatMap {
    case FieldResolveSuccess(_, jo) ⇒ Future successful jo
    case FieldResolveFailure(json) ⇒
      json.validate[JsonApiFailure] match {
        case JsSuccess(f, _) ⇒ Future.failed(f)
        case _: JsError      ⇒ Future.failed(new Exception(s"Cannot resolve $href, cannot parse error: $json"))
      }
  }

  def resolveRef[T: Reads](ref: psgr.expander.protocol.MetaRef[T])(implicit rh: RequestHeader): Future[T] = resolve(ref.meta.href).flatMap { json ⇒
    json.validate[T] match {
      case JsSuccess(f, _) ⇒ Future.successful(f)
      case e: JsError      ⇒ Future.failed(new Exception(s"Cannot resolve $ref, cannot parse json: $json"))
    }
  }

  def expand(json: JsObject)(implicit rh: RequestHeader) = {
    (getFields(rh) match {
      case fields if fields.nonEmpty ⇒
        implicit val rslv = apply(rh)
        JsonExpander(json, fields).expand
      case _ ⇒
        Future.successful(ResolveResult(json.validate[MetaRef].asOpt.fold(Set.empty[MetaRef])(Set(_)), json))
    }).flatMap(metaInterceptor.apply)
  }

  def expandEnum(json: JsObject)(implicit rh: RequestHeader): Enumerator[Array[Byte]] =
    Enumerator flatten expand(json).map { v ⇒
      Enumerator(v.value.toString().getBytes("UTF-8"))
    }
}
