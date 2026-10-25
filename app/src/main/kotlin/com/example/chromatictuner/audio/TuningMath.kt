package com.example.chromatictuner.audio

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class MusicalNote(val name: String, val octave: Int, val midi: Int)

internal data class GuitarString(val label: String, val midi: Int)

internal object TuningMath {
  val guitarStrings = listOf(
    GuitarString("E2", 40), GuitarString("A2", 45), GuitarString("D3", 50),
    GuitarString("G3", 55), GuitarString("B3", 59), GuitarString("E4", 64),
  )
  private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

  fun noteFor(frequencyHz: Double, referenceHz: Int): MusicalNote {
    val midi = (69 + 12 * log2(frequencyHz / referenceHz)).roundToInt()
    return MusicalNote(names[Math.floorMod(midi, 12)], Math.floorDiv(midi, 12) - 1, midi)
  }

  fun frequencyFor(midi: Int, referenceHz: Int): Double =
    referenceHz * 2.0.pow((midi - 69) / 12.0)

  fun centsFrom(frequencyHz: Double, targetMidi: Int, referenceHz: Int): Double =
    1200.0 * log2(frequencyHz / frequencyFor(targetMidi, referenceHz))
}
