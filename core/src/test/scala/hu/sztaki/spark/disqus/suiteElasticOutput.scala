package hu.sztaki.spark.disqus

import com.sksamuel.elastic4s.ElasticDsl.get
import hu.sztaki.spark.{Datum, Elastic}
import com.sksamuel.elastic4s.ElasticDsl._
import org.apache.spark.rdd.RDD

import scala.concurrent.duration.DurationInt

class suiteElasticOutput extends stubeFunSpec {
  configuration = configuration.set[Boolean]("squs.output.elastic-search.enabled", true)

  val elastic = Elastic.Cache.get()

  describe("The Youtube Spark job") {
    var job: Option[Job] = None
    var results: Iterable[Datum] = Iterable.empty

    it("should be able to get created,") {
      job = Some(new Job(List {
        (datum: RDD[Result]) =>
          results =
            results ++ datum.collect().flatMap(_.asSuccess.comments)
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
        results.size should be >= 1
        results.take(2).foreach {
          datum =>
            log.trace("Looking for [{}] in Elasticsearch.", datum.ID.stringify)
            val getResponse = elastic.client.execute(
              get(elastic.&.index, datum.ID.stringify)
            ).await(10 seconds).result
            getResponse.found should be(true)
            log.trace("Found [{}] in Elasticsearch.", datum.ID.stringify)
        }
      }
    }
  }
}
