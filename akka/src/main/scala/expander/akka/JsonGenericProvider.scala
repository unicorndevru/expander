package expander.akka

import java.net.URLEncoder

import play.api.libs.json._

import scala.language.implicitConversions

class JsonGenericProvider(patterns: Seq[ExpandPattern]) extends (JsValue ⇒ Map[JsPath, String]) {

  val readsToString: Reads[String] = Reads[String] {
    case JsString(v)  ⇒ JsSuccess(v)
    case JsNumber(v)  ⇒ JsSuccess(v.toString())
    case JsBoolean(v) ⇒ JsSuccess(v.toString)
    case _            ⇒ JsError()
  }

  def isPlural(s: String) = s.endsWith("s")

  def isSingular(s: String) = !isPlural(s)

  def toSingular(s: String) = s.stripSuffix("s")

  def toPlural(s: String) = s + "s"

  def toQueryString(prefix: Char, queryParams: Map[String, String]): String =
    if (queryParams.isEmpty) ""
    else {
      prefix.toString + queryParams.mapValues(URLEncoder.encode(_, "UTF-8")).map(kv ⇒ kv._1 + "=" + kv._2).mkString("&")
    }

  def collectArrayPatterns(jsObject: JsObject): Seq[ExpandPattern] = {
    val arrayMatches = jsObject.value.collect {
      case (k, jsArray: JsArray) if isPlural(k) ⇒
        toSingular(k) → jsArray.value.map(readsToString.reads).zipWithIndex.collect {
          case (JsSuccess(v, _), i) ⇒
            (i, v)
        }
    }.filter(_._2.nonEmpty)

    patterns.flatMap {
      p ⇒
        val arrApp: Seq[(String, Seq[(Int, String)])] = p.required.values.map(_.path).collect {
          case KeyPathNode(kpn) :: Nil if arrayMatches.contains(kpn) ⇒
            kpn → arrayMatches(kpn)
        }.toSeq
        if (arrApp.size == 1) {
          val (kpn, values) = arrApp.head
          val n = p.required.find(_._2.path == KeyPathNode(kpn) :: Nil).get._1

          values.map {
            case (i, v) ⇒
              val pPath = JsPath((p.path.path.reverse match {
                case RecursiveSearch(rsn) :: tail ⇒
                  IdxPathNode(i) :: KeyPathNode(toPlural(rsn)) :: tail
                case KeyPathNode(sn) :: tail ⇒
                  IdxPathNode(i) :: KeyPathNode(toPlural(sn)) :: tail
                case tail ⇒
                  IdxPathNode(i) :: tail
              }).reverse)

              p.copy(path = pPath, required = p.required - n, applied = p.applied + (n → v))
          }

        } else {
          Seq.empty
        }
    }
  }

  def collectIndirectMatches(jsObject: JsObject): Iterable[Map[JsPath, String]] = jsObject.value.collect {
    case (k, v: JsObject) ⇒
      val prep = JsPath(List(KeyPathNode(k)))
      Seq(apply(v).map { case (kk, vv) ⇒ (prep compose kk, vv) })

    case (k, v: JsArray) if !isPlural(k) ⇒
      val prep = JsPath(List(KeyPathNode(k)))
      v.value.zipWithIndex.collect {
        case (vv: JsObject, i) ⇒
          apply(vv).map { case (kkk, vvv) ⇒ (prep apply i compose kkk, vvv) }
      }
    case (k, v: JsArray) ⇒
      val prep = JsPath(List(KeyPathNode(k)))

      v.value.zipWithIndex.collect {
        case (vv: JsObject, i) ⇒
          apply(vv).map { case (kkk, vvv) ⇒ (prep apply i compose kkk, vvv) }
      }

  }.flatten

  def collectMatches(jsObject: JsObject, addPatterns: Seq[ExpandPattern]): Map[JsPath, String] = (patterns ++ addPatterns).flatMap { pattern ⇒

    val requiredOpt = pattern.required.mapValues(p ⇒ p.json.pick.reads(jsObject).flatMap(readsToString.reads).asOpt)

    if (requiredOpt.forall(_._2.isDefined)) {
      // pattern matched
      val required = requiredOpt.mapValues(_.get)
      val optional = pattern.optional.mapValues(p ⇒ p.json.pick.reads(jsObject).flatMap(readsToString.reads).asOpt).collect {
        case (k, Some(v)) ⇒ (k, v)
      }

      val params = required ++ optional ++ pattern.applied

      val (urlParams, queryParams) = params.partition {
        case (k, v) ⇒
          pattern.urlKeys(k)
      }

      val urlWithSubstitutes = urlParams.foldLeft(pattern.url) {
        case (url, (k, v)) ⇒
          url.replace(":" + k, v)
      }

      val urlWithParams = urlWithSubstitutes + toQueryString(if (urlWithSubstitutes.contains('?')) '&' else '?', queryParams)

      Some(pattern.path → urlWithParams)
    } else {
      // pattern not matched
      None
    }
  }.toMap

  def apply(json: JsValue): Map[JsPath, String] = {

    json match {
      case jo: JsObject ⇒

        val arrPatterns = collectArrayPatterns(jo)

        val directMatches = collectMatches(jo, arrPatterns)

        collectIndirectMatches(jo).foldLeft(directMatches)(_ ++ _)

      case ja: JsArray ⇒
        ja.value.zipWithIndex.collect {
          case (jo: JsObject, i) ⇒ apply(jo).map {
            case (jp, vv) ⇒ JsPath(IdxPathNode(i) :: jp.path) → vv
          }

          case (jaa: JsArray, i) ⇒ apply(jaa).map {
            case (jp, vv) ⇒ JsPath(IdxPathNode(i) :: jp.path) → vv
          }
        }.foldLeft(Map.empty[JsPath, String])(_ ++ _)

      case _ ⇒
        Map.empty[JsPath, String]
    }

  }
}
