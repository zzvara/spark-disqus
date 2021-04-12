package hu.sztaki.spark

trait Objectifiable[T] {
  def apply(string: String): T
}
