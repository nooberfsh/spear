package scraper

import scala.collection.mutable
import scala.language.existentials

import scraper.config.Settings
import scraper.exceptions.{FunctionNotFoundException, TableNotFoundException}
import scraper.expressions.{Count, Expression}
import scraper.plans.logical.dsl._
import scraper.plans.logical.{LogicalPlan, SingleRowRelation}
import scraper.plans.physical.PhysicalPlan

case class FunctionInfo(
  name: String,
  functionClass: Class[_ <: Expression],
  builder: Seq[Expression] => Expression
)

object FunctionInfo {
  def apply[F <: Expression](
    functionClass: Class[F],
    builder: Expression => Expression
  ): FunctionInfo = {
    val name = (functionClass.getSimpleName stripSuffix "$").toUpperCase
    FunctionInfo(name, functionClass, (args: Seq[Expression]) => builder(args.head))
  }
}

trait FunctionRegistry {
  def registerFunction(fn: FunctionInfo): Unit

  def removeFunction(name: String): Unit

  def lookupFunction(name: String): FunctionInfo
}

trait Catalog {
  val functionRegistry: FunctionRegistry

  def registerRelation(tableName: String, analyzedPlan: LogicalPlan): Unit

  def removeRelation(tableName: String): Unit

  def lookupRelation(tableName: String): LogicalPlan
}

class InMemoryCatalog extends Catalog {
  override val functionRegistry: FunctionRegistry = new FunctionRegistry {
    private val functions: mutable.Map[String, FunctionInfo] =
      mutable.Map.empty[String, FunctionInfo]

    override def lookupFunction(name: String): FunctionInfo =
      functions.getOrElse(name.toLowerCase, throw new FunctionNotFoundException(name))

    override def registerFunction(fn: FunctionInfo): Unit = functions(fn.name.toLowerCase) = fn

    override def removeFunction(name: String): Unit = functions -= name
  }

  private val tables: mutable.Map[String, LogicalPlan] = mutable.Map.empty

  functionRegistry.registerFunction(FunctionInfo(classOf[Count], Count))

  override def registerRelation(tableName: String, analyzedPlan: LogicalPlan): Unit =
    tables(tableName) = analyzedPlan

  override def removeRelation(tableName: String): Unit = tables -= tableName

  override def lookupRelation(tableName: String): LogicalPlan =
    tables
      .get(tableName)
      .map(_ subquery tableName)
      .getOrElse(throw new TableNotFoundException(tableName))
}

trait Context {
  type QueryExecution <: plans.QueryExecution

  type Catalog <: scraper.Catalog

  def settings: Settings

  def catalog: Catalog

  /**
   * Parses given query string to a [[scraper.plans.logical.LogicalPlan logical plan]].
   */
  def parse(query: String): LogicalPlan

  /**
   * Analyzes an unresolved [[scraper.plans.logical.LogicalPlan logical plan]] and outputs its
   * strictly-typed version.
   */
  def analyze(plan: LogicalPlan): LogicalPlan

  /**
   * Optimizes a resolved [[scraper.plans.logical.LogicalPlan logical plan]] into another equivalent
   * but more performant version.
   */
  def optimize(plan: LogicalPlan): LogicalPlan

  /**
   * Plans a [[scraper.plans.logical.LogicalPlan logical plan]] into an executable
   * [[scraper.plans.physical.PhysicalPlan physical plan]].
   */
  def plan(plan: LogicalPlan): PhysicalPlan

  def execute(logicalPlan: LogicalPlan): QueryExecution

  lazy val single: DataFrame = new DataFrame(SingleRowRelation, this)

  def single(first: Expression, rest: Expression*): DataFrame = single select first +: rest

  def q(query: String): DataFrame

  def table(name: String): DataFrame
}

object Context {
  implicit class QueryString(query: String)(implicit context: Context) {
    def q: DataFrame = context q query
  }
}
