package spear

import scala.util.control.NonFatal

import org.scalatest.FunSuite

import spear.expressions._
import spear.plans.QueryPlan
import spear.plans.logical.LogicalPlan
import spear.trees.TreeNode
import spear.types.DataType
import spear.utils._

trait TestUtils { this: FunSuite =>
  def assertSideBySide(actual: String, expected: String): Unit = {
    if (expected != actual) {
      fail(sideBySide(
        s"""Actual
           |$expected
           |""".stripMargin,

        s"""Expected
           |$actual
           |""".stripMargin,

        withHeader = true
      ))
    }
  }

  def assertSideBySide[T <: TreeNode[T]](actual: TreeNode[T], expected: TreeNode[T]): Unit =
    if (actual != expected) {
      fail(sideBySide(
        s"""Actual
           |${actual.prettyTree}
           |""".stripMargin,

        s"""Expected
           |${expected.prettyTree}
           |""".stripMargin,

        withHeader = true
      ))
    }

  def checkTree[T <: TreeNode[T]](actual: TreeNode[T], expected: TreeNode[T]): Unit =
    assertSideBySide(actual, expected)

  private def normalizeExpressionIDs[Plan <: QueryPlan[Plan]](plan: Plan): Plan = {
    val namedExpressionsWithID = plan.collectFromAllExpressions {
      case e: NamedExpression if e.isResolved => e
      case e: Alias                           => e
      case e: InternalAlias                   => e
    }.distinct

    val ids = namedExpressionsWithID.map { _.expressionID }.distinct

    val groupedByID = namedExpressionsWithID.groupBy { _.expressionID }.toSeq sortBy {
      // Sorts by reverse ID occurrence order to ensure that:
      //
      //  1. The normalized plan tree is always deterministic, and
      //  2. Expression IDs appearing in leaf plan nodes are minimum.
      case (expressionID, _) => -(ids indexOf expressionID)
    }

    val rewrite = for {
      ((_, namedExpressions), index) <- groupedByID.zipWithIndex.toMap
      named <- namedExpressions
    } yield (named: Expression) -> (named withID ExpressionID(index))

    plan transformAllExpressionsDown rewrite
  }

  def checkPlan[Plan <: QueryPlan[Plan]](actual: Plan, expected: Plan): Unit =
    checkTree(normalizeExpressionIDs(actual), normalizeExpressionIDs(expected))

  def checkDataFrame(actual: DataFrame, expected: DataFrame): Unit =
    checkDataFrame(actual, expected.toSeq)

  def checkDataFrame(df: DataFrame, first: Row, rest: Row*): Unit =
    checkDataFrame(df, first +: rest)

  def checkDataFrame(df: DataFrame, expected: Seq[Row]): Unit = {
    val actual = try {
      df.queryExecution.physicalPlan.iterator.toSeq
    } catch {
      case NonFatal(cause) =>
        fail(
          s"""Query execution failed:
             |
             |${df.explanation(extended = true)}
             |""".stripMargin,
          cause
        )
    }

    if (actual != expected) {
      val answerDiff = sideBySide(
        s"""Actual
           |${actual mkString "\n"}
           |""".stripMargin,

        s"""Expected
           |${expected mkString "\n"}
           |""".stripMargin,

        withHeader = true
      )

      fail(
        s"""Unexpected row(s) detected:
           |$answerDiff
           |
           |Query plan details:
           |
           |${df explanation (extended = true)}
           |""".stripMargin
      )
    }
  }

  def checkWellTyped(e: Expression, dataType: DataType): Unit = {
    if (!e.isWellTyped) {
      fail(
        s"""Expression ${e.debugString} is not well-typed:
           |${e.prettyTree}
           |""".stripMargin
      )
    }

    val actualType = e.strictlyTyped.get.dataType
    if (actualType != dataType) {
      fail(
        s"""Strictly typed form of ${e.debugString} has wrong data type $actualType:
           |${e.strictlyTyped.get.prettyTree}
           |""".stripMargin
      )
    }
  }

  def checkStrictlyTyped(e: Expression, dataType: DataType): Unit = {
    if (!e.isStrictlyTyped) {
      fail(
        s"""Expression ${e.debugString} is not strictly-typed:
           |${e.prettyTree}
           |""".stripMargin
      )
    }

    val actualType = e.strictlyTyped.get.dataType
    if (actualType != dataType) {
      fail(
        s"""Strictly typed form of ${e.debugString} has wrong data type $actualType:
           |${e.prettyTree}
           |""".stripMargin
      )
    }
  }

  def checkWellTyped(plan: LogicalPlan): Unit =
    if (!plan.isWellTyped) {
      fail(
        s"""Logical plan not well-typed:
           |${plan.prettyTree}
           |""".stripMargin
      )
    }

  def checkStrictlyTyped(plan: LogicalPlan): Unit = {
    checkWellTyped(plan)

    if (!plan.isStrictlyTyped) {
      fail(
        s"""Logical plan is well typed but not strictly-typed:
           |
           |# Original logical plan:
           |${plan.prettyTree}
           |
           |# Well-typed logical plan:
           |${plan.strictlyTyped.get.prettyTree}
           |""".stripMargin
      )
    }
  }

  def withTable(context: Context, name: Name)(f: => Unit): Unit = try f finally {
    context.queryExecutor.catalog.removeRelation(name)
  }

  def withTable(name: Name)(f: => Unit)(implicit context: Context): Unit =
    withTable(context, name)(f)
}