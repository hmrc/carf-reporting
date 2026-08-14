/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.carfreporting.itutil

import uk.gov.hmrc.carfreporting.performance.FileInfo

object Reporter {
  def reportResult(
      results: Vector[FileInfo],
      elapsedNs: Long,
      totalBytes: Long,
      memBefore: Long,
      memAfter: Long
  ): Unit = {
    val elapsedMs = elapsedNs / 1e6
    val elapsedS  = elapsedNs / 1e9

    val perFileMs = results.map(_.nano / 1e6).sorted
    val p50       = percentile(perFileMs, 50)
    val p95       = percentile(perFileMs, 95)
    val p99       = percentile(perFileMs, 99)
    val max       = perFileMs.lastOption.getOrElse(0.0)

    val mbTotal   = totalBytes.toDouble / (1024 * 1024)
    val filesPerS = results.size / math.max(elapsedS, 1e-9)
    val mbPerS    = mbTotal / math.max(elapsedS, 1e-9)

    println("---")
    println(f"Wall clock:      $elapsedMs%.0f ms")
    println(f"Throughput:      $filesPerS%.2f files/s | $mbPerS%.1f MB/s")
    println(f"Per-file (ms):   p50=$p50%.1f  p95=$p95%.1f  p99=$p99%.1f  max=$max%.1f")
    println(
      f"Memory used:     ${memBefore.toDouble / (1024 * 1024)}%.1f MB -> ${memAfter.toDouble / (1024 * 1024)}%.1f MB"
    )
  }

  private def percentile(sortedMs: Seq[Double], p: Int): Double =
    if (sortedMs.isEmpty) 0.0
    else {
      val idx = math.min(sortedMs.size - 1, (p.toDouble / 100.0 * sortedMs.size).toInt)
      sortedMs(idx)
    }
}
