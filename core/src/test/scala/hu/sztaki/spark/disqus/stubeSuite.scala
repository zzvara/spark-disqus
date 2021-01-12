package hu.sztaki.spark.disqus

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, CancelAfterFailure}
import org.scalatest.funsuite.AnyFunSuite

class stubeSuite
 extends AnyFunSuite
   with BeforeAndAfterAll
   with BeforeAndAfter
   with stubeTest
   with CancelAfterFailure {}
