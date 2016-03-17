package expander.core

import fastparse.core.Result
import play.api.libs.json._

case class PathRequest(path: JsPath, params: Seq[(String, String)] = Seq.empty, inners: Seq[PathRequest] = Seq.empty) {
  def matchParams(given: JsPath): Option[Seq[(String, String)]] = {
    val prefix = if (path.path.isEmpty) None
    else if (path.path.startsWith(given.path)) {
      Some(JsPath(path.path.drop(given.path.size)))
    } else if (given.path.startsWith(path.path)) {
      Some(JsPath())
    } else {
      val searchIndex = path.path.indexWhere(_.isInstanceOf[RecursiveSearch])
      val idxIndex = given.path.indexWhere(_.isInstanceOf[IdxPathNode])

      if (searchIndex >= 0 && idxIndex == searchIndex && path.path.take(searchIndex) == given.path.take(searchIndex)) {
        Some(JsPath(KeyPathNode(path.path(searchIndex).asInstanceOf[RecursiveSearch].key) +: path.path.drop(searchIndex + 1)))
      } else None
    }

    prefix.map{ r ⇒
      val expand = PathRequest.fold(
        inners.map(i ⇒
          i.copy(path = JsPath((r compose i.path).path match {
            case RecursiveSearch(v) :: rest ⇒
              KeyPathNode(v) :: rest
            case rest ⇒
              rest
          }))) :+ PathRequest(r, params)
      )

      if (expand.isEmpty) params
      else if (r.path.nonEmpty) Seq(Expander.Key → expand.mkString(","))
      else params :+ (Expander.Key → expand.mkString(","))
    }
  }

  override def toString =
    path.toJsonString.stripPrefix("obj") +
      (if (params.isEmpty) "" else s"(${params.map { case (k, v) ⇒ k + ":" + v }.mkString(",")})") +
      (if (inners.isEmpty) "" else if (inners.size == 1) inners.head.toString else s"{${inners.mkString(",")}}")
}

object PathRequest {

  sealed trait RequestToken

  case class PathToken(path: JsPath) extends RequestToken

  case class ParamsToken(params: Seq[(String, String)]) extends RequestToken

  case class InnersToken(inners: Seq[Seq[RequestToken]]) extends RequestToken

  private object Parse {

    import fastparse.all._

    val spaces = P(CharIn(" \t\n").rep)
    val name = P(CharIn("1234567890qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM").rep(min = 1).!)
    val value = P(CharIn("1234567890qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM-+_$%").rep(min = 1).!)
    val number = P(CharIn("01234567890").rep(min = 1).!).map(_.toInt)

    val keyNode: Parser[KeyPathNode] = P(".".? ~ spaces ~ name).map(KeyPathNode)
    val idxNode: Parser[IdxPathNode] = P("[" ~ spaces ~ number ~ spaces ~ "]").map(IdxPathNode)
    val patternNode: Parser[RecursiveSearch] = P("*" ~ spaces ~ name).map(RecursiveSearch)
    val node: Parser[PathNode] = P(spaces ~ (keyNode | idxNode | patternNode) ~ spaces)
    val path: Parser[JsPath] = P(node.rep(min = 1)).map(ns ⇒ JsPath(ns.toList))

    val pathToken: Parser[PathToken] = P(path.map(PathToken))

    val paramsPair: Parser[(String, String)] = P(spaces ~ name ~ spaces ~ ":" ~ spaces ~ value ~ spaces)
    val params: Parser[Seq[(String, String)]] = P("(" ~ paramsPair.rep(sep = ",") ~ ")")

    val paramsToken: Parser[ParamsToken] = P(params.map(ParamsToken))

    lazy val innersToken: Parser[InnersToken] = P("{" ~ innersSeq ~ "}")

    lazy val requestToken: Parser[RequestToken] = P(spaces ~ (pathToken | paramsToken | innersToken) ~ spaces)

    lazy val tokenSeq: Parser[Seq[RequestToken]] = P(requestToken.rep)

    lazy val innersSeq: Parser[InnersToken] = P(spaces ~ tokenSeq.rep(sep = ",").map(InnersToken) ~ spaces)

  }

  private def flatten(token: InnersToken): Seq[PathRequest] = token.inners.filter(_.nonEmpty).map(_.toList).flatMap(chain)

  private def chain(tokens: List[RequestToken]): Seq[PathRequest] = tokens match {
    case PathToken(path) :: Nil ⇒
      Seq(PathRequest(path))

    case (inners: InnersToken) :: Nil ⇒
      flatten(inners)

    case PathToken(path) :: ParamsToken(params) :: tail ⇒
      Seq(PathRequest(path, params, chain(tail)))

    case PathToken(path) :: (inners: InnersToken) :: Nil ⇒
      flatten(inners).map(r ⇒ r.copy(path = path compose r.path))

    case _ ⇒
      Seq.empty
  }

  private def isSubpathOf(sup: JsPath)(sub: JsPath) = sub.path.startsWith(sup.path)

  private def groupPaths(paths: List[JsPath]): Map[JsPath, Seq[JsPath]] = paths match {
    case head :: tail ⇒
      val (subs, rest) = tail.span(isSubpathOf(head))
      groupPaths(rest) + (head → subs)

    case Nil ⇒
      Map.empty
  }

  private def fold(reqs: Seq[PathRequest]): Seq[PathRequest] = {
    val pathMap = reqs.groupBy(_.path).filterKeys(_.path.nonEmpty).mapValues { rs ⇒
      rs.reduce((a, b) ⇒ a.copy(params = a.params ++ b.params, inners = fold(a.inners ++ b.inners)))
    }

    val pathsGrouped = groupPaths(pathMap.keys.toList.sortBy(_.path.size))

    pathsGrouped.map {
      case (p, subs) if subs.isEmpty ⇒ pathMap(p)
      case (p, subs) if subs.size == 1 && pathMap(p).inners.isEmpty && pathMap(p).params.isEmpty ⇒ pathMap(subs.head)
      case (p, subs) ⇒
        val subReqs = subs.map(pathMap(_))
        val req = pathMap(p)
        req.copy(inners = fold(req.inners ++ subReqs.map(sr ⇒ sr.copy(path = JsPath(sr.path.path.drop(p.path.size))))))
    }.toSeq
  }

  def parse(line: String): Seq[PathRequest] = Parse.innersSeq.parse(line) match {
    case Result.Success(inners, _) ⇒
      fold(flatten(inners))
    case _: Result.Failure ⇒
      Seq.empty
  }
}