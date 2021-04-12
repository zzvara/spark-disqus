package hu.sztaki.spark

trait Generable[Identifier] extends Serializable {
  def generate: Identifier = Generable.not
}

object Generable {
  def not: Nothing = throw new IllegalStateException("Not generable!")
}
