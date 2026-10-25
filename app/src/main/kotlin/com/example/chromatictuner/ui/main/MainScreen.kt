package com.example.chromatictuner.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.example.chromatictuner.audio.TunerAudioEngine
import com.example.chromatictuner.audio.TuningMath
import com.example.chromatictuner.ui.theme.ChromaticTunerTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import java.util.Locale

private enum class TunerMode { CHROMATIC, GUITAR }

private data class TunerReading(
  val note: String,
  val octave: Int,
  val frequencyHz: Double,
  val cents: Double,
)

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

/** Retains the generated navigation entrypoint; the tuner needs no extra screens. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val preferences = remember(context) { context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE) }
  val lifecycleOwner = LocalLifecycleOwner.current

  var mode by rememberSaveable { mutableStateOf(TunerMode.CHROMATIC) }
  var guitarString by rememberSaveable { mutableIntStateOf(0) }
  var referenceHz by remember { mutableIntStateOf(preferences.getInt("reference_hz", 440)) }
  var tolerance by remember { mutableIntStateOf(preferences.getInt("tolerance", 3)) }
  var showSettings by remember { mutableStateOf(false) }
  var showModeMenu by remember { mutableStateOf(false) }

  var microphoneRequested by rememberSaveable { mutableStateOf(false) }
  var permissionGranted by remember {
    mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
  }
  var permissionDenied by remember { mutableStateOf(false) }
  var audioError by remember { mutableStateOf<String?>(null) }
  var frequencyHz by remember { mutableStateOf<Double?>(null) }
  var foreground by remember {
    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_RESUME) {
        foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (event == Lifecycle.Event.ON_RESUME) {
          permissionGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
          if (permissionGranted) permissionDenied = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    permissionGranted = granted
    permissionDenied = !granted
    if (granted) {
      microphoneRequested = true
      audioError = null
    }
  }

  // Cancellation automatically stops/releases AudioRecord when the app goes to background,
  // the permission is revoked, or the user disables the microphone.
  LaunchedEffect(microphoneRequested, permissionGranted, foreground) {
    if (!microphoneRequested || !permissionGranted || !foreground) {
      frequencyHz = null
      return@LaunchedEffect
    }
    var smoothed: Double? = null
    var lastSeen = SystemClock.elapsedRealtime()
    try {
      TunerAudioEngine(context).listen { detected ->
        val now = SystemClock.elapsedRealtime()
        if (detected != null) {
          // Exponential smoothing for a stable needle, without smoothing across note changes.
          val previous = smoothed
          val close = previous != null && abs(1200.0 * log2(detected / previous)) < 65.0
          smoothed = if (close) previous!! * 0.6 + detected * 0.4 else detected
          frequencyHz = smoothed
          lastSeen = now
        } else if (now - lastSeen > 550) {
          frequencyHz = null
          smoothed = null
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      audioError = error.localizedMessage ?: "Не удалось открыть микрофон"
      microphoneRequested = false
    } finally {
      frequencyHz = null
    }
  }

  val reading = frequencyHz?.let { hz ->
    val midi = if (mode == TunerMode.GUITAR) {
      TuningMath.guitarStrings[guitarString].midi
    } else {
      TuningMath.noteFor(hz, referenceHz).midi
    }
    val note = if (mode == TunerMode.GUITAR) {
      // The displayed note is the selected tuning target, not an invented detection.
      TuningMath.noteFor(TuningMath.frequencyFor(midi, referenceHz), referenceHz)
    } else {
      TuningMath.noteFor(hz, referenceHz)
    }
    TunerReading(note.name, note.octave, hz, TuningMath.centsFrom(hz, midi, referenceHz))
  }

  TunerScreen(
    reading = reading,
    mode = mode,
    guitarString = guitarString,
    referenceHz = referenceHz,
    tolerance = tolerance,
    listening = microphoneRequested && permissionGranted && foreground,
    permissionDenied = permissionDenied,
    audioError = audioError,
    modifier = modifier,
    onMicrophoneClick = {
      if (microphoneRequested) {
        microphoneRequested = false
      } else if (permissionGranted) {
        audioError = null
        microphoneRequested = true
      } else {
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
      }
    },
    onSettingsClick = { showSettings = true },
    onModeClick = { showModeMenu = true },
    modeExpanded = showModeMenu,
    onModeDismiss = { showModeMenu = false },
    onModeSelect = { newMode -> mode = newMode; showModeMenu = false },
    onStringSelect = { guitarString = it },
    onPermissionSettingsClick = {
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
      })
    },
  )

  if (showSettings) {
    TunerSettingsDialog(
      referenceHz = referenceHz,
      tolerance = tolerance,
      onReferenceChanged = {
        referenceHz = it
        preferences.edit().putInt("reference_hz", it).apply()
      },
      onToleranceChanged = {
        tolerance = it
        preferences.edit().putInt("tolerance", it).apply()
      },
      onDismiss = { showSettings = false },
    )
  }
}

@Composable
private fun TunerScreen(
  reading: TunerReading?,
  mode: TunerMode,
  guitarString: Int,
  referenceHz: Int,
  tolerance: Int,
  listening: Boolean,
  permissionDenied: Boolean,
  audioError: String?,
  onMicrophoneClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onModeClick: () -> Unit,
  modeExpanded: Boolean,
  onModeDismiss: () -> Unit,
  onModeSelect: (TunerMode) -> Unit,
  onStringSelect: (Int) -> Unit,
  onPermissionSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier.fillMaxSize().background(TunerColors.background)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    val compact = maxHeight < 700.dp
    val scrolling = if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
      modifier = Modifier.fillMaxSize().then(scrolling).padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TunerHeader(listening, onMicrophoneClick, onSettingsClick)
      TunerDivider()

      if (compact) Spacer(Modifier.height(30.dp)) else Spacer(Modifier.weight(1f))
      ReadingDisplay(reading, tolerance, mode)
      if (compact) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))

      PitchScale(reading?.cents, tolerance)
      Spacer(Modifier.height(12.dp))
      CentsDisplay(reading?.cents, tolerance)

      if (compact) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))
      TunerDivider()
      Box(Modifier.fillMaxWidth()) {
        SettingRow("MODE", if (mode == TunerMode.CHROMATIC) "Chromatic" else "Guitar", true, onModeClick)
        DropdownMenu(expanded = modeExpanded, onDismissRequest = onModeDismiss) {
          DropdownMenuItem(text = { Text("Chromatic") }, onClick = { onModeSelect(TunerMode.CHROMATIC) })
          DropdownMenuItem(text = { Text("Guitar · Standard E") }, onClick = { onModeSelect(TunerMode.GUITAR) })
        }
      }
      TunerDivider()
      SettingRow("REFERENCE", "A4 = $referenceHz Hz", false, onSettingsClick)

      if (mode == TunerMode.GUITAR) {
        Spacer(Modifier.height(12.dp))
        GuitarStrings(guitarString, onStringSelect)
      }
      if (compact) Spacer(Modifier.height(28.dp)) else Spacer(Modifier.weight(1f))
      TunerStatus(listening, permissionDenied, audioError, onPermissionSettingsClick)
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun TunerHeader(listening: Boolean, onMicrophoneClick: () -> Unit, onSettingsClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(64.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text("CHROMATIC", color = TunerColors.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Text("TUNER", color = TunerColors.muted, fontSize = 10.sp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onMicrophoneClick, modifier = Modifier.semantics {
        contentDescription = if (listening) "Отключить микрофон" else "Включить микрофон"
      }) {
        MicrophoneGlyph(if (listening) TunerColors.green else TunerColors.muted)
      }
      IconButton(onClick = onSettingsClick, modifier = Modifier.semantics { contentDescription = "Настройки тюнера" }) {
        SettingsGlyph()
      }
    }
  }
}

@Composable
private fun ReadingDisplay(reading: TunerReading?, tolerance: Int, mode: TunerMode) {
  val cents = reading?.cents
  val inTune = cents != null && abs(cents) <= tolerance
  val message = when {
    cents == null -> "ОЖИДАНИЕ ЗВУКА"
    inTune -> "СТРОЙ ТОЧНЫЙ"
    abs(cents) > 150 && mode == TunerMode.GUITAR -> "ПРОВЕРЬ ВЫБРАННУЮ СТРУНУ"
    cents < 0 -> "ПОДТЯНИ СТРУНУ"
    else -> "ОСЛАБЬ СТРУНУ"
  }
  val accent = if (inTune) TunerColors.green else if (cents == null) TunerColors.muted else TunerColors.orange

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(if (mode == TunerMode.GUITAR) "НАСТРАИВАЕМАЯ СТРУНА" else "РАСПОЗНАННАЯ НОТА",
      color = TunerColors.muted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(reading?.note ?: "—", color = TunerColors.foreground, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
      if (reading != null) Text(reading.octave.toString(), color = TunerColors.muted,
        fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(if (reading == null) "—" else String.format(Locale.US, "%.2f", reading.frequencyHz),
        color = TunerColors.foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.size(4.dp))
      Text("Hz", color = TunerColors.muted, fontSize = 18.sp)
    }
    Spacer(Modifier.height(16.dp))
    Text(message, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun PitchScale(cents: Double?, tolerance: Int) {
  val markerPosition by animateFloatAsState(
    targetValue = (cents ?: 0.0).toFloat().coerceIn(-50f, 50f),
    animationSpec = tween(150), label = "pitch needle",
  )
  Column(modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
      val axisY = size.height * 0.52f
      val startX = 14.dp.toPx()
      val endX = size.width - startX
      val centerX = size.width / 2f
      val span = endX - startX
      val markerColor = if (cents != null && abs(cents) <= tolerance) TunerColors.green else TunerColors.orange
      drawLine(TunerColors.track, Offset(startX, axisY), Offset(endX, axisY),
        strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
      drawRoundRect(TunerColors.greenDim, Offset(centerX - 15.dp.toPx(), axisY - 4.dp.toPx()),
        Size(30.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))

      for (tick in 0..20) {
        val x = startX + span * tick / 20f
        val major = tick % 5 == 0
        val halfHeight = (if (major) 17.dp else 10.dp).toPx()
        drawLine(if (tick == 10) TunerColors.green else TunerColors.muted.copy(alpha = 0.52f),
          Offset(x, axisY - halfHeight), Offset(x, axisY + halfHeight),
          strokeWidth = if (tick == 10) 2.dp.toPx() else 1.dp.toPx())
      }

      if (cents != null) {
        val markerX = centerX + span * markerPosition / 100f
        drawLine(markerColor, Offset(markerX, axisY - 28.dp.toPx()),
          Offset(markerX, axisY + 26.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        val markerTop = axisY - 31.dp.toPx()
        val arrow = Path().apply {
          moveTo(markerX - 7.dp.toPx(), markerTop)
          lineTo(markerX + 7.dp.toPx(), markerTop)
          lineTo(markerX, markerTop + 9.dp.toPx())
          close()
        }
        drawPath(arrow, color = markerColor)
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("−50", color = TunerColors.muted, fontSize = 12.sp)
      Text("0", color = TunerColors.green, fontSize = 12.sp)
      Text("+50", color = TunerColors.muted, fontSize = 12.sp)
    }
  }
}

@Composable
private fun CentsDisplay(cents: Double?, tolerance: Int) {
  val inTune = cents != null && abs(cents) <= tolerance
  Row(modifier = Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text = if (cents == null) "—" else "%+d".format(Locale.US, cents.roundToInt()),
      color = if (cents == null) TunerColors.muted else if (inTune) TunerColors.green else TunerColors.orange,
      fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.size(5.dp))
    Text("cents", color = TunerColors.muted, fontSize = 12.sp)
  }
}

@Composable
private fun SettingRow(label: String, value: String, showChevron: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick),
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
private fun GuitarStrings(selected: Int, onSelect: (Int) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    TuningMath.guitarStrings.forEachIndexed { index, string ->
      val active = selected == index
      Box(
        modifier = Modifier.weight(1f).height(43.dp)
          .clip(RoundedCornerShape(9.dp))
          .background(if (active) TunerColors.greenBackground else TunerColors.background)
          .border(1.dp, if (active) TunerColors.greenDim else TunerColors.divider, RoundedCornerShape(9.dp))
          .clickable { onSelect(index) }
          .semantics { contentDescription = "Струна ${string.label}" },
        contentAlignment = Alignment.Center,
      ) {
        Text(string.label, fontSize = 12.sp,
          color = if (active) TunerColors.green else TunerColors.foreground,
          fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
      }
    }
  }
}

@Composable
private fun TunerStatus(
  listening: Boolean,
  permissionDenied: Boolean,
  audioError: String?,
  openSettings: () -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(TunerColors.greenBackground),
        contentAlignment = Alignment.Center) { WaveGlyph() }
      Text(
        text = when {
          audioError != null -> "Ошибка микрофона: $audioError"
          permissionDenied -> "Нет доступа к микрофону"
          listening -> "Listening for sound..."
          else -> "Нажми на микрофон, чтобы начать"
        },
        color = TunerColors.muted, fontSize = 11.sp,
      )
    }
    if (permissionDenied) {
      TextButton(onClick = openSettings) { Text("Разрешения приложения") }
    }
  }
}

@Composable
private fun TunerSettingsDialog(
  referenceHz: Int,
  tolerance: Int,
  onReferenceChanged: (Int) -> Unit,
  onToleranceChanged: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Настройки тюнера") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Опорная частота: A4 = $referenceHz Hz")
        Slider(value = referenceHz.toFloat(), onValueChange = { onReferenceChanged(it.roundToInt()) },
          valueRange = 430f..450f, steps = 19)
        Text("Точность настройки: ±$tolerance cents")
        Slider(value = tolerance.toFloat(), onValueChange = { onToleranceChanged(it.roundToInt()) },
          valueRange = 1f..5f, steps = 3)
        Text("Микрофон работает только при включённом прослушивании и открытом приложении.",
          fontSize = 12.sp, color = TunerColors.muted)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
  )
}

@Composable
private fun TunerDivider() {
  HorizontalDivider(thickness = 1.dp, color = TunerColors.divider)
}

// Lightweight icons drawn in Compose; no dependency on Material Icons.
@Composable
private fun MicrophoneGlyph(color: Color) {
  Canvas(modifier = Modifier.size(20.dp)) {
    val w = size.width
    val h = size.height
    val stroke = 1.6.dp.toPx()
    drawRoundRect(color, Offset(w * 0.36f, h * 0.12f), Size(w * 0.28f, h * 0.49f),
      CornerRadius(w * 0.14f), style = Stroke(stroke))
    val curve = Path().apply {
      moveTo(w * 0.20f, h * 0.47f)
      cubicTo(w * 0.20f, h * 0.85f, w * 0.80f, h * 0.85f, w * 0.80f, h * 0.47f)
    }
    drawPath(curve, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
    drawLine(color, Offset(w * 0.50f, h * 0.75f), Offset(w * 0.50f, h * 0.91f), stroke)
    drawLine(color, Offset(w * 0.34f, h * 0.91f), Offset(w * 0.66f, h * 0.91f), stroke)
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
      drawLine(TunerColors.green, Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half),
        strokeWidth = 1.7.dp.toPx(), cap = StrokeCap.Round)
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TunerScreenPreview() {
  ChromaticTunerTheme {
    TunerScreen(
      reading = TunerReading("E", 2, 84.24, 38.0),
      mode = TunerMode.CHROMATIC, guitarString = 0, referenceHz = 440, tolerance = 3,
      listening = true, permissionDenied = false, audioError = null,
      onMicrophoneClick = {}, onSettingsClick = {}, onModeClick = {},
      modeExpanded = false, onModeDismiss = {}, onModeSelect = {}, onStringSelect = {},
      onPermissionSettingsClick = {},
    )
  }
}
