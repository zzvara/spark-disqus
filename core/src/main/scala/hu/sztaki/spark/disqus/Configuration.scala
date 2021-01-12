package hu.sztaki.spark.disqus

class Configuration(silent: Boolean = false)(implicit
factory: Factory.forConfiguration[Configuration])
 extends configuration.Configuration[Configuration](
   "squs.conf",
   "squs.defaults.conf",
   true,
   Some("squs"),
   silent
 )
   with Serializable

object Configuration {

  implicit object configurationFactory extends Factory.forConfiguration[Configuration] {

    override def apply(
      fromFile: String,
      fromEnvironment: Boolean,
      restrictTo: Option[String],
      silent: Boolean
    ): Configuration = new Configuration(silent)

  }

}
