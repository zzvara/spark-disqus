package hu.sztaki.spark

trait Stringifiable {
  def stringify: String
  override def toString: String = stringify
}
