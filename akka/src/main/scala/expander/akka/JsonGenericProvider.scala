package expander.akka

import java.net.URLEncoder

import play.api.libs.json._

import scala.language.implicitConversions

class JsonGenericProvider(patterns: Seq[ResolvePattern]) extends (JsValue ⇒ Map[JsPath, String]) {

  val readsToString: Reads[String] = Reads[String] {
    case JsString(v)  ⇒ JsSuccess(v)
    case JsNumber(v)  ⇒ JsSuccess(v.toString())
    case JsBoolean(v) ⇒ JsSuccess(v.toString)
    case _            ⇒ JsError()
  }

  def apply(json: JsValue): Map[JsPath, String] = {

    json match {
      case jo: JsObject ⇒
        val indirectMatchesProcess: Iterable[Either[(String, Int, String), Map[JsPath, String]]] = jo.value.collect {
          case (k, v: JsObject) ⇒
            val prep = JsPath(List(KeyPathNode(k)))
            Seq(Right[(String, Int, String), Map[JsPath, String]](apply(v).map { case (kk, vv) ⇒ (prep compose kk, vv) }))

          case (k, v: JsArray) if !k.endsWith("s") ⇒
            val prep = JsPath(List(KeyPathNode(k)))
            v.value.zipWithIndex.collect {
              case (vv: JsObject, i) ⇒
                Right(apply(vv).map { case (kkk, vvv) ⇒ (prep apply i compose kkk, vvv) })
            }
          case (k, v: JsArray) ⇒
            val prep = JsPath(List(KeyPathNode(k)))

            v.value.zipWithIndex.collect {
              case (vv: JsObject, i) ⇒
                Right(apply(vv).map { case (kkk, vvv) ⇒ (prep apply i compose kkk, vvv) })
              case (JsString(vv), i) ⇒
                Left((k, i, vv))
              case (JsNumber(vv), i) ⇒
                Left((k, i, vv.toString()))
              case (JsBoolean(vv), i) ⇒ Left((k, i, vv.toString))
            }

        }.flatten

        val indirectMatchesResolved = indirectMatchesProcess.collect { case Right(m) ⇒ m }

        val arrayMatchesProcess = indirectMatchesProcess.collect { case Left(m) if m._1.endsWith("s") ⇒ (m._1.stripSuffix("s"), (m._2, m._3)) }.groupBy(_._1).mapValues(_.map(_._2))

        val arrPatterns = patterns.flatMap {
          p ⇒
            val arrApp = p.required.values.map(_.path).collect {
              case KeyPathNode(kpn) :: Nil if arrayMatchesProcess.contains(kpn) ⇒
                kpn → arrayMatchesProcess(kpn)
            }
            if (arrApp.size == 1) {
              val (kpn, values) = arrApp.head
              val n = p.required.find(_._2.path == KeyPathNode(kpn) :: Nil).get._1
              values.map {
                case (i, vvvvvv) ⇒
                  val pPath = JsPath((p.path.path.reverse match {
                    case RecursiveSearch(rsn) :: tail ⇒
                      IdxPathNode(i) :: KeyPathNode(rsn + "s") :: tail
                    case KeyPathNode(sn) :: tail ⇒
                      IdxPathNode(i) :: KeyPathNode(sn + "s") :: tail
                    case tail ⇒
                      IdxPathNode(i) :: tail
                  }).reverse)
                  p.copy(path = pPath, required = p.required - n, applied = p.applied + (n → vvvvvv))
              }

            } else {
              Seq.empty
            }
        }

        val directMatches = (patterns ++ arrPatterns).flatMap { pattern ⇒

          val requiredOpt = pattern.required.mapValues(p ⇒ p.json.pick.reads(jo).flatMap(readsToString.reads).asOpt)

          if (requiredOpt.forall(_._2.isDefined)) {
            // pattern matched
            val required = requiredOpt.mapValues(_.get)
            val optional = pattern.optional.mapValues(p ⇒ p.json.pick.reads(jo).flatMap(readsToString.reads).asOpt).collect {
              case (k, Some(v)) ⇒ (k, v)
            }

            val params = required ++ optional ++ pattern.applied

            val (queryParams, urlParams) = params.partition {
              case (k, v) ⇒
                pattern.query(k)
            }

            val urlWithSubstitutes = urlParams.foldLeft(pattern.url) {
              case (url, (k, v)) ⇒
                url.replace(":" + k, v)
            }

            val urlWithParams = urlWithSubstitutes + (if (queryParams.isEmpty) "" else (if (urlWithSubstitutes.contains('?')) '&' else '?') + queryParams.mapValues(URLEncoder.encode(_, "UTF-8")).map(kv ⇒ kv._1 + "=" + kv._2).mkString("&"))

            Some(pattern.path → urlWithParams)
          } else {
            // pattern not matched
            None
          }
        }.toMap

        indirectMatchesResolved.foldLeft(directMatches)(_ ++ _)

      case ja: JsArray ⇒
        ja.value.zipWithIndex.collect {
          case (jo: JsObject, i) ⇒ apply(jo).map {
            case (jp, vv) ⇒ JsPath(IdxPathNode(i) :: jp.path) → vv
          }
        }.foldLeft(Map.empty[JsPath, String])(_ ++ _)

      case _ ⇒
        Map.empty
    }

  }
}
