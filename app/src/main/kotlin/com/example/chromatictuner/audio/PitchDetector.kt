package com.example.chromatictuner.audio

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YIN-style pitch detection for one mono 16-bit PCM window.
 * 44.1 kHz audio is decimated by four before the difference calculation.
 * Expected practical range: roughly 60-1200 Hz (including all standard guitar strings).
 */
internal class PitchDetector(
  private val sampleRate: Int = 44_100,
) {
  private val downsample = 4
  private val downsampledRate = sampleRate.toDouble() / downsample
  private val minFrequency = 60.0
  private val maxFrequency = 1200.0
  private val threshold = 0.15

  fun detect(pcm: ShortArray): Double? {
    val count = pcm.size / downsample
    if (count < 512) return null

    val samples = DoubleArray(count)
    var sum = 0.0
    for (i in 0 until count) {
      var total = 0.0
      for (j in 0 until downsample) total += pcm[i * downsample + j].toDouble()
      val value = total / (downsample * Short.MAX_VALUE.toDouble())
      samples[i] = value
      sum += value
    }

    val mean = sum / count
    var energy = 0.0
    for (i in samples.indices) {
      samples[i] -= mean
      energy += samples[i] * samples[i]
    }
    val rms = sqrt(energy / count)
    // Ignore quiet rooms and weak microphone hiss.
    if (rms < 0.009) return null

    val minLag = (downsampledRate / maxFrequency).toInt().coerceAtLeast(2)
    val maxLag = min((downsampledRate / minFrequency).toInt(), count / 2)
    if (minLag >= maxLag) return null

    val difference = DoubleArray(maxLag + 1)
    val comparisonLength = count - maxLag
    for (lag in 1..maxLag) {
      var sumSquares = 0.0
      for (i in 0 until comparisonLength) {
        val d = samples[i] - samples[i + lag]
        sumSquares += d * d
      }
      difference[lag] = sumSquares
    }

    val normalized = DoubleArray(maxLag + 1)
    normalized[0] = 1.0
    var cumulative = 0.0
    for (lag in 1..maxLag) {
      cumulative += difference[lag]
      normalized[lag] = if (cumulative > 1e-12) difference[lag] * lag / cumulative else 1.0
    }

    // Prefer the first strong periodic minimum, not the lowest minimum at an octave.
    var bestLag = -1
    for (lag in minLag until maxLag) {
      if (normalized[lag] < threshold) {
        var localLag = lag
        while (localLag < maxLag && normalized[localLag + 1] < normalized[localLag]) localLag++
        bestLag = localLag
        break
      }
    }
    // Looser fallback accommodates plucked strings with overtones.
    if (bestLag == -1) {
      var bestScore = 0.23
      for (lag in minLag..maxLag) {
        if (normalized[lag] < bestScore) {
          bestScore = normalized[lag]
          bestLag = lag
        }
      }
    }
    if (bestLag == -1) return null

    val y0 = normalized[(bestLag - 1).coerceAtLeast(1)]
    val y1 = normalized[bestLag]
    val y2 = normalized[(bestLag + 1).coerceAtMost(maxLag)]
    val denominator = y0 - 2 * y1 + y2
    val adjustment = if (abs(denominator) > 1e-9) (0.5 * (y0 - y2) / denominator).coerceIn(-1.0, 1.0) else 0.0
    val result = downsampledRate / (bestLag + adjustment)
    return result.takeIf { it.isFinite() && it in minFrequency..maxFrequency }
  }
}
