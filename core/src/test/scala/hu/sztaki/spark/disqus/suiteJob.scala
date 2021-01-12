package hu.sztaki.spark.disqus

class suiteJob extends stubeFunSpec {
  describe("The Youtube Spark job") {
    var job: Option[Job] = None
    var results: Iterable[Result] = Iterable.empty

    it("should be able to get created,") {
      job = Some(new Job(List {
        comments => results = results ++ comments.collect()
      }))
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
