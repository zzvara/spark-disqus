package hu.sztaki.spark.youtube

class suiteJob extends stubeFunSpec {
  describe("The Youtube Spark job") {
    var job: Option[Job] = None
    var results: Iterable[Datum] = Iterable.empty

    it("should be able to get created,") {
      job = Some(new Job(List(_.foreachRDD {
        datum => results = results ++ datum.collect()
      })))
    }
    it("should be able to get started,") {
      job.get.start()
    }
    it("should be able to finish with first batch job,") {
      job.get.awaitProcessing()
    }
    it("should eventually fetch some data,") {
      eventually {
        results.size should be > 0
        results.foreach(println(_))
      }
    }
  }
}
