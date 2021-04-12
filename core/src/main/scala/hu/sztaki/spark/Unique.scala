package hu.sztaki.spark

@SerialVersionUID(-6245704577824300780L)
abstract class Unique[Identifier <: Stringifiable](protected val identifier: Identifier)
 extends Serializable with Equals {

  def canEqual(other: Any): Boolean = other.isInstanceOf[Unique[Identifier]]

  override def equals(other: Any): Boolean =
    other match {
      case that: Unique[_] =>
        (this eq that) || (
          that.canEqual(this) &&
            hashCode == that.hashCode() &&
            ID == that.ID
        )
      case _ => false
    }

  override def hashCode(): Int = 31 * ID.##

  def ID: Identifier = identifier
}
