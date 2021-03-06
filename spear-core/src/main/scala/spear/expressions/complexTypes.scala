package spear.expressions

import spear.{Name, Row}
import spear.expressions.typecheck.{Foldable, TypeConstraint}
import spear.types._

case class MakeNamedStruct(children: Seq[Expression]) extends Expression {
  assert(children.length % 2 == 0)

  override def isNullable: Boolean = false

  override def nodeName: String = "named_struct"

  override def evaluate(input: Row): Any = Row.fromSeq(values map { _ evaluate input })

  override protected def typeConstraint: TypeConstraint =
    names sameTypeAs StringType andAlso Foldable concat values.anyType

  override protected lazy val strictDataType: DataType = {
    val fields = (
      evaluatedNames,
      values map { _.dataType },
      values map { _.isNullable }
    ).zipped map {
        (name, dataType, nullable) => StructField(Name.caseSensitive(name), dataType, nullable)
      }

    StructType(fields)
  }

  override protected def template(childList: Seq[String]): String = {
    val (nameStrings, valueStrings) = childList splitAt names.length
    val argStrings = nameStrings zip valueStrings flatMap { case (name, value) => Seq(name, value) }
    argStrings mkString (s"$nodeName(", ", ", ")")
  }

  private lazy val (names, values) = children.splitAt(children.length / 2)

  private lazy val evaluatedNames: Seq[String] = names map { _.evaluated } map {
    case n: String => n
  }
}

object MakeNamedStruct {
  def apply(names: Seq[Expression], values: Seq[Expression]): MakeNamedStruct =
    MakeNamedStruct(names ++ values)
}

case class MakeArray(values: Seq[Expression]) extends Expression {
  assert(values.nonEmpty)

  override def isNullable: Boolean = false

  override def children: Seq[Expression] = values

  override def nodeName: String = "array"

  override def evaluate(input: Row): Any = values map { _ evaluate input }

  override protected def typeConstraint: TypeConstraint = values.sameType

  override protected lazy val strictDataType: DataType =
    ArrayType(values.head.dataType, values exists (_.isNullable))
}

case class MakeMap(children: Seq[Expression]) extends Expression {
  assert(children.length % 2 == 0)

  override def nodeName: String = "map"

  override def isNullable: Boolean = false

  override def evaluate(input: Row): Any =
    (keys.map { _ evaluate input } zip values.map { _ evaluate input }).toMap

  override protected def typeConstraint: TypeConstraint = keys.sameType concat values.sameType

  override protected lazy val strictDataType: DataType = {
    val valueNullable = values exists { _.isNullable }
    MapType(keys.head.dataType, values.head.dataType, valueNullable)
  }

  private lazy val (keys, values) = children.splitAt(children.length / 2)
}

object MakeMap {
  def apply(keys: Seq[Expression], values: Seq[Expression]): MakeMap = MakeMap(keys ++ values)
}
