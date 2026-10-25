package com.example.chromatictuner.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Reads PCM on a worker thread. No sound is stored, transmitted, or written to disk. */
internal class TunerAudioEngine(private val context: Context) {
  private val sampleRate = 44_100
  private val windowSize = 4096
  private val chunkSize = 1024
  private val detector = PitchDetector(sampleRate)

  @SuppressLint("MissingPermission") // Explicitly checked before constructing AudioRecord.
  suspend fun listen(onPitch: (Double?) -> Unit) = withContext(Dispatchers.Default) {
    check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      "Microphone permission not granted"
    }
    val minSize = AudioRecord.getMinBufferSize(
      sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )
    check(minSize > 0) { "Audio input is not available" }

    val record = AudioRecord(
      MediaRecorder.AudioSource.MIC,
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
      maxOf(minSize, chunkSize * 8),
    )
    try {
      check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
      record.startRecording()
      check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" }

      val ring = ShortArray(windowSize)
      val chunk = ShortArray(chunkSize)
      var cursor = 0
      var stored = 0
      var sinceAnalysis = 0

      while (currentCoroutineContext().isActive) {
        val count = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
        check(count > 0) { "Microphone read error: $count" }
        for (i in 0 until count) {
          ring[cursor] = chunk[i]
          cursor = (cursor + 1) % ring.size
        }
        stored = (stored + count).coerceAtMost(ring.size)
        sinceAnalysis += count
        if (stored < ring.size || sinceAnalysis < 4096) continue
        sinceAnalysis = 0

        val samples = ShortArray(ring.size)
        for (i in samples.indices) samples[i] = ring[(cursor + i) % ring.size]
        val hz = detector.detect(samples)
        withContext(Dispatchers.Main.immediate) { onPitch(hz) }
      }
    } finally {
      if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        runCatching { record.stop() }
      }
      record.release()
    }
  }
}
