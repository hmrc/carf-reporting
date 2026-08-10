package uk.gov.hmrc.carfreporting.performance

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}
import uk.gov.hmrc.carfreporting.base.NoGuiceSpecBase
import uk.gov.hmrc.carfreporting.dispatchers.{DispatcherName, XmlDispatcher}
import uk.gov.hmrc.carfreporting.services.XmlParserService

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class XmlParserPerformanceTestSpec extends NoGuiceSpecBase with BeforeAndAfterEach with BeforeAndAfterAll {

  import Reporter.*

  override def beforeEach(): Unit = {
    println("Cleaning")
    System.gc()
  }

  override def beforeAll(): Unit = {
    println("Warming up")
    val smallDispatcher = new DispatcherName {
      val name: String = "small-xml-dispatcher"
    }

    val xmlDispatcher = new XmlDispatcher(actorSystem, smallDispatcher)
    val service = new XmlParserService()(xmlDispatcher)

    val validCarfXmlSizeOnDisk = 4000L

    lazy val calls = Vector(
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service)
    )
    val _ = Await.result(Future.sequence(calls), 10.seconds)
    println("Warm up complete")
  }

  val validCarfXmlSizeOnDisk = 4000L //4kb (size on disk)
  val invalidCarfXmlSizeOnDisk = 4000L
  val twoFiftyMbInBytes = 274726912L //262mb (size on disk)
  val bigInvalidXmlSizeOnDisk = 12000L //12kb (size on disk)

  "XmlParserService (small thread pool)" - {
    val smallDispatcher = new DispatcherName {
      val name: String = "small-xml-dispatcher"
    }

    val xmlDispatcher = new XmlDispatcher(actorSystem, smallDispatcher)
    val service = new XmlParserService()(xmlDispatcher)
    
    "must handle a small batch of small valid and invalid XML files (4kb)" in {
      smallBatchOfSmall(service)
    }

    "must handle a large batch of small valid and invalid XML files(4kb)" in {
      largeBatchOfSmall(service)
    }

    "must handle a small batch of large valid and invalid XML files (262MB) & (12kb)" in {
      smallBatchOfLarge(service)
    }

    "must handle a large batch of large valid and invalid XML files (250mb) & (12kb)" in {
      largeBatchOfLarge(service)
    }
  }
  
  "XmlParserService (medium thread pool)" - { // fixed-pool-size = 8, optimal pool size for m5.xlarge
    val smallDispatcher = new DispatcherName {
      val name: String = "xml-dispatcher"
    }

    val xmlDispatcher = new XmlDispatcher(actorSystem, smallDispatcher)
    val service = new XmlParserService()(xmlDispatcher)
    
    "must handle a small batch of small valid and invalid XML files (4kb)" in {
      smallBatchOfSmall(service)
    }

    "must handle a large batch of small valid and invalid XML files(4kb)" in {
      largeBatchOfSmall(service)
    }

    "must handle a small batch of large valid and invalid XML files (262MB) & (12kb)" in {
      smallBatchOfLarge(service)
    }

    "must handle a large batch of large valid and invalid XML files (250mb) & (12kb)" in {
      largeBatchOfLarge(service)
    }
  }
  
  "XmlParserService (large thread pool)" - { // fixed-pool-size = 16, optimal pool size for Mac M5 Chip 
    val smallDispatcher = new DispatcherName {
      val name: String = "large-xml-dispatcher"
    }

    val xmlDispatcher = new XmlDispatcher(actorSystem, smallDispatcher)
    val service = new XmlParserService()(xmlDispatcher)
    
    "must handle a small batch of small valid and invalid XML files (4kb)" in {
      smallBatchOfSmall(service)
    }

    "must handle a large batch of small valid and invalid XML files(4kb)" in {
      largeBatchOfSmall(service)
    }

    "must handle a small batch of large valid and invalid XML files (262MB) & (12kb)" in {
      smallBatchOfLarge(service)
    }

    "must handle a large batch of large valid and invalid XML files (250mb) & (12kb)" in {
      largeBatchOfLarge(service)
    }
  }

  private def createAndMeasureExecution(path: String, fileSizeInBytes: Long, service: => XmlParserService) =
    Future {
      val startTime = System.nanoTime()
      service.validateAndExtract(path).value map { _ =>
        FileInfo(fileSizeInBytes, System.nanoTime() - startTime)
      }
    }.flatten

  private def usedMemory(rt: Runtime): Long = rt.totalMemory() - rt.freeMemory()

  private def smallBatchOfSmall(service: XmlParserService) = {
    lazy val calls = Vector( //keep lazy Futures are eager
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/invalid-carf.xml", invalidCarfXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/invalid-carf.xml", invalidCarfXmlSizeOnDisk, service)
    )

    val runtime = Runtime.getRuntime

    val memBefore = usedMemory(runtime)
    val started = System.nanoTime()

    val results = Await.result(Future.sequence(calls), 10.seconds)

    val elapsedNs = System.nanoTime() - started
    val memAfter = usedMemory(runtime)

    val totalBytes = results.map(_.sizeInBytes).sum

    reportResult(results, elapsedNs, totalBytes, memBefore, memAfter)
    results.size mustBe 5
  }

  private def largeBatchOfSmall(service: XmlParserService) = {
    lazy val validFiles = (1 to 25).map(_ =>
      createAndMeasureExecution("data/examples/valid-carf.xml", validCarfXmlSizeOnDisk, service)
    )

    lazy val invalidFiles = (1 to 25).map(_ =>
      createAndMeasureExecution("data/examples/invalid-carf.xml", invalidCarfXmlSizeOnDisk, service)
    )

    lazy val calls = (validFiles ++ invalidFiles).toVector

    val runtime = Runtime.getRuntime

    val memBefore = usedMemory(runtime)
    val started = System.nanoTime()

    val results = Await.result(Future.sequence(calls), 10.seconds)

    val elapsedNs = System.nanoTime() - started
    val memAfter = usedMemory(runtime)

    val totalBytes = results.map(_.sizeInBytes).sum

    reportResult(results, elapsedNs, totalBytes, memBefore, memAfter)
    results.size mustBe 50
  }
  
  private def smallBatchOfLarge(service: XmlParserService) = {
    lazy val calls = Vector(
      createAndMeasureExecution("data/sized/carf-262mb.xml", twoFiftyMbInBytes, service),
      createAndMeasureExecution("data/sized/carf-262mb.xml", twoFiftyMbInBytes, service),
      createAndMeasureExecution("data/sized/carf-262mb.xml", twoFiftyMbInBytes, service),
      createAndMeasureExecution("data/examples/too-many-schema-errors.xml", bigInvalidXmlSizeOnDisk, service),
      createAndMeasureExecution("data/examples/too-many-schema-errors.xml", bigInvalidXmlSizeOnDisk, service)
    )

    val runtime = Runtime.getRuntime

    val memBefore = usedMemory(runtime)
    val started = System.nanoTime()

    val results = Await.result(Future.sequence(calls), 10.seconds)

    val elapsedNs = System.nanoTime() - started
    val memAfter = usedMemory(runtime)

    val totalBytes = results.map(_.sizeInBytes).sum

    reportResult(results, elapsedNs, totalBytes, memBefore, memAfter)
    results.size mustBe 5
  }
  
  private def largeBatchOfLarge(service: XmlParserService) = {
    lazy val validFiles = (1 to 25).map(_ =>
      createAndMeasureExecution("data/sized/carf-262mb.xml", twoFiftyMbInBytes, service)
    )

    lazy val invalidFiles = (1 to 25).map(_ =>
      createAndMeasureExecution("data/examples/too-many-schema-errors.xml", bigInvalidXmlSizeOnDisk, service)
    )

    lazy val calls = (validFiles ++ invalidFiles).toVector

    val runtime = Runtime.getRuntime

    val memBefore = usedMemory(runtime)
    val started = System.nanoTime()

    val results = Await.result(Future.sequence(calls), 10.seconds)

    val elapsedNs = System.nanoTime() - started
    val memAfter = usedMemory(runtime)

    val totalBytes = results.map(_.sizeInBytes).sum

    reportResult(results, elapsedNs, totalBytes, memBefore, memAfter)
    results.size mustBe 50
  }
}

case class FileInfo(sizeInBytes: Long, nano: Long)

object Reporter {
  def reportResult(results: Vector[FileInfo],
                   elapsedNs: Long,
                   totalBytes: Long,
                   memBefore: Long,
                   memAfter: Long): Unit = {
    val elapsedMs = elapsedNs / 1e6
    val elapsedS = elapsedNs / 1e9

    val perFileMs = results.map(_.nano / 1e6).sorted
    val p50 = percentile(perFileMs, 50)
    val p95 = percentile(perFileMs, 95)
    val p99 = percentile(perFileMs, 99)
    val max = perFileMs.lastOption.getOrElse(0.0)

    val mbTotal = totalBytes.toDouble / (1024 * 1024)
    val filesPerS = results.size / math.max(elapsedS, 1e-9)
    val mbPerS = mbTotal / math.max(elapsedS, 1e-9)

    println("---")
    println(f"Wall clock:      $elapsedMs%.0f ms")
    println(f"Throughput:      $filesPerS%.2f files/s | $mbPerS%.1f MB/s")
    println(f"Per-file (ms):   p50=$p50%.1f  p95=$p95%.1f  p99=$p99%.1f  max=$max%.1f")
    println(f"Memory used:     ${memBefore.toDouble / (1024 * 1024)}%.1f MB -> ${memAfter.toDouble / (1024 * 1024)}%.1f MB")
  }

  private def percentile(sortedMs: Seq[Double], p: Int): Double = {
    if (sortedMs.isEmpty) 0.0
    else {
      val idx = math.min(sortedMs.size - 1, (p.toDouble / 100.0 * sortedMs.size).toInt)
      sortedMs(idx)
    }
  }
}
