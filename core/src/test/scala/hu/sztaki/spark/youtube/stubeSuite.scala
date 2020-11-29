package hu.sztaki.spark.youtube

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, CancelAfterFailure}
import org.scalatest.funsuite.AnyFunSuite

class stubeSuite
 extends AnyFunSuite
   with BeforeAndAfterAll
   with BeforeAndAfter
   with stubeTest
   with CancelAfterFailure {}
