package psgr.expander.core

import scala.concurrent.{ ExecutionContext, Future }

trait JsonResolver {
  def ctx: ExecutionContext = scala.concurrent.ExecutionContext.global

  def apply(ref: MetaRef): Future[FieldResolveResult] = apply(ref, Field.empty)

  def apply(ref: MetaRef, field: Field): Future[FieldResolveResult]
}
