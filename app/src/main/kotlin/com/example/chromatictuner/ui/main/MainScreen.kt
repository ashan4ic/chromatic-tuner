package com.example.chromatictuner.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.chromatictuner.ui.theme.ChromaticTunerTheme

// Static demonstration values for v0.1. The audio engine will provide these later.
private data class TunerReading(
  val note: String,
  val octave: Int,
  val frequencyHz: String,
  val cents: Int,
)

private val previewReading = TunerReading(note = "E", octave = 2, frequencyHz = "84.24", cents = 38)

private object TunerColors {
  val background = Color(0xFF111820)
  val foreground = Color(0xFFE7EDF3)
  val muted = Color(0xFF91A1AF)
  val divider = Color(0xFF293541)
  val track = Color(0xFF2B3945)
  val green = Color(0xFF39DDA7)
  val greenDim = Color(0xFF1C765A)
  val greenBackground = Color(0xFF174034)
  val orange = Color(0xFFFF9165)
}

/** The navigation signature is retained from the generated project. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  TunerScreen(reading = previewReading, modifier = modifier)
}

@Composable
private fun TunerScreen(reading: TunerReading, modifier: Modifier = Modifier) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(TunerColors.background)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Short displays can scroll; regular phone screens show the complete tuner.
    val compact = maxHeight < 620.dp
    val scrolling = if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
      modifier = Modifier
        .fillMaxSize()
        .then(scrolling)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TunerHeader()
      TunerDivider()

      if (compact) Spacer(Modifier.height(30.dp)) else Spacer(Modifier.weight(1f))
      ReadingDisplay(reading)
      if (compact) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))

      PitchScale(cents = reading.cents)
      Spacer(Modifier.height(12.dp))
      CentsDisplay(cents = reading.cents)

      if (compact) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))
      TunerDivider()
      SettingRow(label = "MODE", value = "Chromatic", showChevron = true)
      TunerDivider()
      SettingRow(label = "REFERENCE", value = "A4 = 440 Hz")

      if (compact) Spacer(Modifier.height(28.dp)) else Spacer(Modifier.weight(1f))
      DemoStatus()
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun TunerHeader() {
  Row(
    modifier = Modifier.fillMaxWidth().height(64.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text("CHROMATIC", color = TunerColors.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Text("TUNER", color = TunerColors.muted, fontSize = 10.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      MicrophoneGlyph()
      SettingsGlyph()
    }
  }
}

@Composable
private fun ReadingDisplay(reading: TunerReading) {
  val isInTune = kotlin.math.abs(reading.cents) <= 3
  val message = when {
    isInTune -> "СТРОЙ ТОЧНЫЙ"
    reading.cents < 0 -> "ПОДТЯНИ СТРУНУ"
    else -> "ОСЛАБЬ СТРУНУ"
  }
  val accent = if (isInTune) TunerColors.green else TunerColors.orange

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("РАСПОЗНАННАЯ НОТА", color = TunerColors.muted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(reading.note, color = TunerColors.foreground, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
      Text(reading.octave.toString(), color = TunerColors.muted, fontSize = 18.sp,
        modifier = Modifier.padding(bottom = 2.dp))
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(reading.frequencyHz, color = TunerColors.foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.size(4.dp))
      Text("Hz", color = TunerColors.muted, fontSize = 18.sp)
    }
    Spacer(Modifier.height(16.dp))
    Text(message, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun PitchScale(cents: Int) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
      val axisY = size.height * 0.52f
      val startX = 14.dp.toPx()
      val endX = size.width - startX
      val centerX = size.width / 2f
      val span = endX - startX
      val inTune = kotlin.math.abs(cents) <= 3
      val markerColor = if (inTune) TunerColors.green else TunerColors.orange

      drawLine(
        color = TunerColors.track,
        start = Offset(startX, axisY),
        end = Offset(endX, axisY),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawRoundRect(
        color = TunerColors.greenDim,
        topLeft = Offset(centerX - 15.dp.toPx(), axisY - 4.dp.toPx()),
        size = Size(30.dp.toPx(), 8.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx()),
      )

      for (tick in 0..20) {
        val x = startX + span * tick / 20f
        val major = tick % 5 == 0
        val halfHeight = (if (major) 17.dp else 10.dp).toPx()
        drawLine(
          color = if (tick == 10) TunerColors.green else TunerColors.muted.copy(alpha = 0.52f),
          start = Offset(x, axisY - halfHeight),
          end = Offset(x, axisY + halfHeight),
          strokeWidth = if (tick == 10) 2.dp.toPx() else 1.dp.toPx(),
        )
      }

      val markerX = startX + span * ((cents.coerceIn(-50, 50) + 50f) / 100f)
      drawLine(
        color = markerColor,
        start = Offset(markerX, axisY - 28.dp.toPx()),
        end = Offset(markerX, axisY + 26.dp.toPx()),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
      )
      val markerTop = axisY - 31.dp.toPx()
      val arrow = Path().apply {
        moveTo(markerX - 7.dp.toPx(), markerTop)
        lineTo(markerX + 7.dp.toPx(), markerTop)
        lineTo(markerX, markerTop + 9.dp.toPx())
        close()
      }
      drawPath(arrow, color = markerColor)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("−50", color = TunerColors.muted, fontSize = 12.sp)
      Text("0", color = TunerColors.green, fontSize = 12.sp)
      Text("+50", color = TunerColors.muted, fontSize = 12.sp)
    }
  }
}

@Composable
private fun CentsDisplay(cents: Int) {
  val inTune = kotlin.math.abs(cents) <= 3
  Row(
    modifier = Modifier.height(38.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = if (cents > 0) "+$cents" else "$cents",
      color = if (inTune) TunerColors.green else TunerColors.orange,
      fontSize = 22.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.size(5.dp))
    Text("cents", color = TunerColors.muted, fontSize = 12.sp)
  }
}

@Composable
private fun TunerDivider() {
  HorizontalDivider(thickness = 1.dp, color = TunerColors.divider)
}

@Composable
private fun SettingRow(label: String, value: String, showChevron: Boolean = false) {
  Row(
    modifier = Modifier.fillMaxWidth().height(58.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, fontSize = 12.sp, color = TunerColors.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(value, fontSize = 14.sp, color = TunerColors.foreground, fontWeight = FontWeight.SemiBold)
      if (showChevron) ChevronGlyph()
    }
  }
}

@Composable
private fun DemoStatus() {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
      modifier = Modifier.size(34.dp).clip(CircleShape).background(TunerColors.greenBackground),
      contentAlignment = Alignment.Center,
    ) {
      WaveGlyph()
    }
    Text("Demo · microphone off", color = TunerColors.muted, fontSize = 11.sp)
  }
}

// Vector-like icons drawn directly in Compose: no extra dependency on Material Icons.
@Composable
private fun MicrophoneGlyph() {
  Canvas(modifier = Modifier.size(20.dp)) {
    val w = size.width
    val h = size.height
    val stroke = 1.6.dp.toPx()
    drawRoundRect(
      color = TunerColors.muted,
      topLeft = Offset(w * 0.36f, h * 0.12f),
      size = Size(w * 0.28f, h * 0.49f),
      cornerRadius = CornerRadius(w * 0.14f),
      style = Stroke(stroke),
    )
    val curve = Path().apply {
      moveTo(w * 0.20f, h * 0.47f)
      cubicTo(w * 0.20f, h * 0.85f, w * 0.80f, h * 0.85f, w * 0.80f, h * 0.47f)
    }
    drawPath(curve, color = TunerColors.muted, style = Stroke(stroke, cap = StrokeCap.Round))
    drawLine(TunerColors.muted, Offset(w * 0.50f, h * 0.75f), Offset(w * 0.50f, h * 0.91f), stroke)
    drawLine(TunerColors.muted, Offset(w * 0.34f, h * 0.91f), Offset(w * 0.66f, h * 0.91f), stroke)
  }
}

@Composable
private fun SettingsGlyph() {
  Canvas(modifier = Modifier.size(20.dp)) {
    val w = size.width
    val h = size.height
    val stroke = 1.6.dp.toPx()
    drawLine(TunerColors.muted, Offset(w * 0.12f, h * 0.31f), Offset(w * 0.88f, h * 0.31f), stroke)
    drawLine(TunerColors.muted, Offset(w * 0.12f, h * 0.73f), Offset(w * 0.88f, h * 0.73f), stroke)
    drawCircle(TunerColors.background, radius = w * 0.13f, center = Offset(w * 0.63f, h * 0.31f))
    drawCircle(TunerColors.muted, radius = w * 0.12f, center = Offset(w * 0.63f, h * 0.31f), style = Stroke(stroke))
    drawCircle(TunerColors.background, radius = w * 0.13f, center = Offset(w * 0.36f, h * 0.73f))
    drawCircle(TunerColors.muted, radius = w * 0.12f, center = Offset(w * 0.36f, h * 0.73f), style = Stroke(stroke))
  }
}

@Composable
private fun ChevronGlyph() {
  Canvas(modifier = Modifier.size(12.dp)) {
    val stroke = 1.4.dp.toPx()
    val path = Path().apply {
      moveTo(size.width * 0.18f, size.height * 0.38f)
      lineTo(size.width * 0.50f, size.height * 0.67f)
      lineTo(size.width * 0.82f, size.height * 0.38f)
    }
    drawPath(path, color = TunerColors.foreground, style = Stroke(stroke, cap = StrokeCap.Round))
  }
}

@Composable
private fun WaveGlyph() {
  Canvas(modifier = Modifier.size(19.dp)) {
    val heights = floatArrayOf(0.24f, 0.48f, 0.80f, 0.46f, 0.24f)
    heights.forEachIndexed { index, fraction ->
      val x = (index + 1) * size.width / 6f
      val half = size.height * fraction / 2f
      drawLine(
        color = TunerColors.green,
        start = Offset(x, size.height / 2f - half),
        end = Offset(x, size.height / 2f + half),
        strokeWidth = 1.7.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TunerScreenPreview() {
  ChromaticTunerTheme {
    TunerScreen(reading = previewReading)
  }
}
