import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.8.0"
  private val hmrcMongoVersion = "2.13.0"
  private val catsVersion      = "2.13.0"
  private val woodstoxVersion  = "6.5.1"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"           %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "org.typelevel"         %% "cats-core"                 % catsVersion,
    "com.fasterxml.woodstox" % "woodstox-core"             % woodstoxVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test
  )

  val it: Seq[ModuleID] = Seq.empty
}
