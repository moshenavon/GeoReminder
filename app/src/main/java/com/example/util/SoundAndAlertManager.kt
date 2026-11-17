package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RingtonePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object SoundAndAlertManager {
    private const val CHANNEL_ID_ENTRY = "geo_alert_entry_channel"
    private const val CHANNEL_ID_EXIT = "geo_alert_exit_channel"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeRingtone: Ringtone? = null
    private var activeAudioTrack: AudioTrack? = null
    private var stopJob: Job? = null

    private val _isPlayingSound = MutableStateFlow(false)
    val isPlayingSound: StateFlow<Boolean> = _isPlayingSound.asStateFlow()

    private val _activeSoundLabel = MutableStateFlow<String?>(null)
    val activeSoundLabel: StateFlow<String?> = _activeSoundLabel.asStateFlow()

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val entryChannel = NotificationChannel(
                CHANNEL_ID_ENTRY,
                "התראות כניסה לרדיוס",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראות המופעלות בעת זיהוי כניסה לרדיוס מיקום בטווח הזמנים המוגדר"
                enableVibration(true)
            }

            val exitChannel = NotificationChannel(
                CHANNEL_ID_EXIT,
                "התראות יציאה מהרדיוס",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראות המופעלות בעת זיהוי יציאה מרדיוס מיקום בטווח הזמנים המוגדר"
                enableVibration(true)
            }

            manager.createNotificationChannel(entryChannel)
            manager.createNotificationChannel(exitChannel)
        }
    }

    fun playAlertSound(
        context: Context,
        preset: RingtonePreset,
        customUriString: String = "",
        vibrate: Boolean = true,
        isEntry: Boolean = true
    ) {
        stopCurrentSound()

        if (vibrate) {
            triggerVibration(context, isEntry)
        }

        _isPlayingSound.value = true
        _activeSoundLabel.value = preset.titleHe

        scope.launch {
            try {
                when (preset) {
                    RingtonePreset.RADAR_CHIME -> {
                        // Ascending harmonic radar chime (C5 -> E5 -> G5 -> C6)
                        playSynthesizedSequence(
                            notesHz = listOf(523.25 to 180, 659.25 to 180, 783.99 to 200, 1046.50 to 420),
                            pauseMs = 45
                        )
                    }
                    RingtonePreset.SONAR_PULSE -> {
                        // Double tactical sonar ping (880Hz, 1320Hz repeated twice)
                        playSynthesizedSequence(
                            notesHz = listOf(
                                880.0 to 160,
                                1320.0 to 240,
                                0.0 to 180,
                                880.0 to 160,
                                1320.0 to 320
                            ),
                            pauseMs = 30
                        )
                    }
                    RingtonePreset.SIREN_ALERT -> {
                        // Urgent alternating siren & descending exit alert
                        playSynthesizedSequence(
                            notesHz = listOf(
                                987.77 to 220,
                                659.25 to 220,
                                987.77 to 220,
                                659.25 to 220,
                                880.0 to 260,
                                523.25 to 400
                            ),
                            pauseMs = 25
                        )
                    }
                    RingtonePreset.SYSTEM_NOTIFICATION -> {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        playSystemRingtoneWithFallback(context, uri, isEntry)
                    }
                    RingtonePreset.SYSTEM_ALARM -> {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        playSystemRingtoneWithFallback(context, uri, isEntry)
                    }
                    RingtonePreset.SYSTEM_RINGTONE -> {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        playSystemRingtoneWithFallback(context, uri, isEntry)
                    }
                    RingtonePreset.CUSTOM_URI -> {
                        val uri = if (customUriString.isNotBlank()) {
                            Uri.parse(customUriString)
                        } else {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        }
                        playSystemRingtoneWithFallback(context, uri, isEntry)
                    }
                }
            } catch (_: Exception) {
                _isPlayingSound.value = false
                _activeSoundLabel.value = null
            }
        }
    }

    private suspend fun playSystemRingtoneWithFallback(
        context: Context,
        uri: Uri?,
        isEntry: Boolean
    ) {
        var playedSystem = false
        if (uri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    activeRingtone = ringtone
                    ringtone.play()
                    playedSystem = true
                }
            } catch (_: Exception) {
                playedSystem = false
            }
        }

        // Also play a subtle synthesized chime if system ringtone failed or finished immediately
        if (!playedSystem) {
            if (isEntry) {
                playSynthesizedSequence(
                    notesHz = listOf(587.33 to 200, 880.0 to 350),
                    pauseMs = 40
                )
            } else {
                playSynthesizedSequence(
                    notesHz = listOf(880.0 to 200, 587.33 to 350),
                    pauseMs = 40
                )
            }
        } else {
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(5500)
                stopCurrentSound()
            }
        }
    }

    private suspend fun playSynthesizedSequence(
        notesHz: List<Pair<Double, Int>>,
        pauseMs: Long
    ) {
        val sampleRate = 24000
        for ((freq, durationMs) in notesHz) {
            if (!_isPlayingSound.value) break
            if (freq <= 0.0) {
                delay(durationMs.toLong())
                continue
            }
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
            val samples = ShortArray(numSamples)
            val fadeSamples = (numSamples * 0.12).toInt().coerceAtLeast(1)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Fundamental + soft octave overtone for rich radar chime sound
                val wave = 0.75 * sin(2.0 * PI * freq * t) + 0.25 * sin(4.0 * PI * freq * t)
                val envelope = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                samples[i] = (wave * envelope * Short.MAX_VALUE * 0.7).toInt().toShort()
            }

            try {
                val bufferSize = (samples.size * 2).coerceAtLeast(
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeAudioTrack = track
                track.write(samples, 0, samples.size)
                track.play()
                delay(durationMs.toLong() + pauseMs)
                track.stop()
                track.release()
            } catch (_: Exception) {
                delay(durationMs.toLong())
            }
        }
        _isPlayingSound.value = false
        _activeSoundLabel.value = null
    }

    fun stopCurrentSound() {
        stopJob?.cancel()
        stopJob = null
        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
            }
        } catch (_: Exception) {
        }
        activeRingtone = null

        try {
            activeAudioTrack?.let {
                it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        activeAudioTrack = null

        _isPlayingSound.value = false
        _activeSoundLabel.value = null
    }

    private fun triggerVibration(context: Context, isEntry: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            val pattern = if (isEntry) {
                longArrayOf(0, 180, 100, 280)
            } else {
                longArrayOf(0, 300, 120, 300, 120, 400)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    fun postZoneTransitionNotification(
        context: Context,
        zoneName: String,
        isEntry: Boolean,
        distanceMeters: Float,
        radiusMeters: Float,
        ringtoneName: String
    ) {
        ensureNotificationChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isEntry) CHANNEL_ID_ENTRY else CHANNEL_ID_EXIT
        val title = if (isEntry) {
            "כניסה לרדיוס: $zoneName"
        } else {
            "יציאה מהרדיוס: $zoneName"
        }
        val body = if (isEntry) {
            "זוהתה כניסה לטווח (${distanceMeters.toInt()} מ׳ מתוך רדיוס ${radiusMeters.toInt()} מ׳) • צלצול: $ringtoneName"
        } else {
            "זוהתה יציאה מהטווח (${distanceMeters.toInt()} מ׳ מחוץ לרדיוס ${radiusMeters.toInt()} מ׳) • צלצול: $ringtoneName"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
