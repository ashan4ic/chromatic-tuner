package com.example.chromatictuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchDetectorTest {
  private val detector = PitchDetector()

  private fun sine(hz: Double): ShortArray = ShortArray(4096) { index ->
    (12000.0 * sin(2 * PI * hz * index / 44100.0)).toInt().toShort()
  }

  @Test fun detectsLowEString() {
    val detected = detector.detect(sine(82.4069))
    assertTrue("Expected low E", detected != null && abs(detected - 82.4069) < 1.0)
  }

  @Test fun detectsHighEString() {
    val detected = detector.detect(sine(329.6276))
    assertTrue("Expected high E", detected != null && abs(detected - 329.6276) < 2.0)
  }

  @Test fun ignoresSilence() {
    assertNull(detector.detect(ShortArray(4096)))
  }

  @Test fun readsNotesAndCents() {
    assertEquals("A", TuningMath.noteFor(440.0, 440).name)
    assertEquals(4, TuningMath.noteFor(440.0, 440).octave)
    assertEquals(0.0, TuningMath.centsFrom(440.0, 69, 440), 0.001)
    assertEquals(440.0, TuningMath.frequencyFor(69, 440), 0.001)
  }
}
