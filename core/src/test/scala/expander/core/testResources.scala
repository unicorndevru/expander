package expander.core

import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object testResources {

  trait ResolveById[T] {
    def getById(id: String): Future[T]
  }

  protected def leaf[TT: Writes: ResolveById](id: String) =
    ResourceContext[TT] {
      params ⇒
        implicitly[ResolveById[TT]].getById(id).map(implicitly[Writes[TT]].writes)
    }

  protected def ref[TT: Writes: ResolveById: ExpandContext](id: String) =
    ResourceContext[TT] {
      params ⇒
        implicitly[ResolveById[TT]].getById(id).flatMap(Expander(_, params.groupBy(_._1).get(Expander.Key).map(kv ⇒ kv.flatMap(v ⇒ PathRequest.parse(v._2))).getOrElse(Seq.empty): _*))
    }

  case class Wrapper(id: String, fooId: String, barId: String)

  implicit val wrapperW = Json.writes[Wrapper]

  case class ArrayWrapper(id: String, ids: Seq[String])

  implicit val arrayWrapperW = Json.writes[ArrayWrapper]

  case class Inside(id: String)

  implicit val insideW = Json.writes[Inside]

  case class Complex(awId: String, wrapId: String)

  implicit val complexW = Json.writes[Complex]

  case class Recursive(ids: Seq[String])

  implicit val recursiveW = Json.writes[Recursive]

  implicit object InsideResolve extends ResolveById[Inside] {
    override def getById(id: String) = {
      Future.successful(Inside(id))
    }
  }

  implicit object ArrayExpandContext extends ExpandContext[ArrayWrapper] with ResolveById[ArrayWrapper] {
    override def resources(root: ArrayWrapper) =
      root.ids.zipWithIndex.map{ case (id, i) ⇒ (__ \ "arr" apply i) → leaf[Inside](id) }.toMap

    override def getById(id: String) = {
      Future.successful(ArrayWrapper(id, Seq("id1", "id2")))
    }
  }

  implicit object WrapperExpandContext extends ExpandContext[Wrapper] with ResolveById[Wrapper] {
    override def resources(root: Wrapper) = Map(
      (__ \ "sub" \ "foo") → leaf[Inside](root.fooId),
      (__ \ "sub" \ "bar") → leaf[Inside](root.barId),
      (__ \ "fooo") → leaf[Inside](root.fooId),
      (__ \ "baar") → leaf[Inside](root.barId)
    )

    override def getById(id: String) = {
      Future.successful(Wrapper(id, "expFoo", "expBar"))
    }
  }

  implicit object ComplexExpandContext extends ExpandContext[Complex] {
    override def resources(root: Complex) = Map(
      (__ \ "arrw") → ref[ArrayWrapper](root.awId),
      (__ \ "wrap") → ref[Wrapper](root.wrapId)
    )
  }

  implicit object RecursiveExpandContext extends ExpandContext[Recursive] {
    override def resources(root: Recursive) =
      root.ids.zipWithIndex.map{ case (id, i) ⇒ (__ \ "arr" apply i) → ref[Wrapper](id) }.toMap
  }
}
