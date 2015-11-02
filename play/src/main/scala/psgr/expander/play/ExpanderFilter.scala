package psgr.expander.play

import javax.inject.Inject

import play.api.Logger
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._

import scala.concurrent.Future

class ExpanderFilter @Inject() (metaResolver: MetaResolver) extends EssentialFilter {

  val log = Logger("expander-filter")

  import metaResolver.expandedHeader
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override def apply(next: EssentialAction): EssentialAction = new EssentialAction {
    override def apply(rh: RequestHeader): Iteratee[Array[Byte], Result] = {
      next(rh).mapM {
        case r if r.header.headers.get(expandedHeader).isDefined ⇒
          log.trace("has expander header")

          r.body.run(Iteratee.ignore).map { _ ⇒
            val endTime = System.currentTimeMillis() - r.header.headers(expandedHeader).toLong

            r.withHeaders(expandedHeader → endTime.toString)
          }
        case r if r.header.status >= 200 && r.header.status < 300 && r.header.headers.get("Content-Type").fold(false)(_.contains("json")) ⇒

          log.trace("going to resolve")

          metaResolver.getFields(rh) match {
            case fields if fields.nonEmpty ⇒
              log.trace("Expand: " + fields + " / " + r.header)

              val startTime = System.currentTimeMillis()

              r.body.run(Iteratee.getChunks).map(_.reduceLeft(_ ++ _)).map(Json.parse).map {
                case jo: JsObject ⇒
                  val fullRh = rh.copy(headers = rh.headers.add(metaResolver.passResponseHeaders.map(h ⇒ r.header.headers.get(h).map(h → _)).collect {
                    case Some(kv) ⇒ kv
                  }: _*))
                  metaResolver.expandEnum(jo)(fullRh)
                case jv ⇒
                  Enumerator(jv.toString().getBytes("UTF-8"))
              }.map { expandedBody ⇒
                r.copy(body = expandedBody).withHeaders(expandedHeader → (System.currentTimeMillis() - startTime).toString)
              }

            case _ ⇒
              Future.successful(r)
          }

        case r ⇒
          Future successful r

      }
    }
  }

}
