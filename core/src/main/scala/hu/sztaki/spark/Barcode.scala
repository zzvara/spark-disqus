package hu.sztaki.spark

import com.google.common.hash.Hashing
import org.apache.commons.lang3.{RandomStringUtils, StringUtils}

case class Barcode(value: String) extends Stringifiable with Serializable {
  validate()

  override def toString: String = value
  override def stringify: String = value

  def canEqual(other: Any): Boolean = other.isInstanceOf[Barcode]

  override def equals(other: Any): Boolean =
    other match {
      case that: Barcode =>
        (that.canEqual(this)) &&
          value == that.value
      case _ => false
    }

  override def hashCode(): Int =
    Seq(value).map(_.hashCode()).foldLeft(0)(
      (a, b) => 31 * a + b
    )

  def isValid: Boolean = StringUtils.isAlphanumeric(value) && value.length == Barcode.BARCODE_LENGTH
  def validate(): Unit = if (!isValid) throw new Exception(s"Barcode invalid [$value]!")
}

object Barcode {
  private val BARCODE_LENGTH = 32

  def byHashingFrom(string: String): Barcode =
    Barcode(
      Hashing.murmur3_128(42).hashBytes(string.getBytes()).toString
    )

  implicit object Factory extends Generable[Barcode] with Objectifiable[Barcode] {

    override def generate: Barcode =
      new Barcode(RandomStringUtils.randomAlphanumeric(BARCODE_LENGTH))

    override def apply(string: String): Barcode = Barcode(string)
  }

}
