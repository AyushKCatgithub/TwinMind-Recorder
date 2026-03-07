package com.example.twinmind.service.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.twinmind.MainActivity
import com.example.twinmind.R
import com.example.twinmind.data.repository.RecordingRepository
import com.example.twinmind.utils.AudioUtils
import com.example.twinmind.utils.Constants
import com.example.twinmind.utils.StorageUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed class RecordingStatus(val displayText: String) {
    data object Idle : RecordingStatus("Idle")
    data object Recording : RecordingStatus("Recording...")
    data object PausedPhoneCall : RecordingStatus("Paused - Phone call")
    data object PausedAudioFocus : RecordingStatus("Paused - Audio focus lost")
    data object Stopped : RecordingStatus("Stopped")
}

@AndroidEntryPoint
class AudioRecordingService : Service() {

    @Inject
    lateinit var recordingRepository: RecordingRepository

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isCapturing = AtomicBoolean(false)
    @Volatile private var sessionId = -1L
    @Volatile private var chunkSequence = 0
    @Volatile private var isPaused = false
    @Volatile private var pauseReason = ""
    @Volatile private var recordingStartTimeMs = 0L
    @Volatile private var pauseStartTimeMs = 0L
    @Volatile private var totalPausedMs = 0L
    @Volatile private var lastSoundTimeMs = 0L
    @Volatile private var silenceWarningActive = false

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!isPaused) pauseRecording("PAUSED_AUDIO_FOCUS")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPaused && pauseReason == "PAUSED_AUDIO_FOCUS") resumeRecording()
            }
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    showWarningNotification("Microphone source changed")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : android.telephony.PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallState(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        createNotificationChannels()
        registerHeadsetReceiver()
        registerPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START -> {
                val newSessionId = intent.getLongExtra(Constants.EXTRA_SESSION_ID, -1L)
                if (newSessionId != -1L) {
                    sessionId = newSessionId
                    startForeground(Constants.NOTIFICATION_ID, buildNotification("Recording...", "00:00"))
                    startRecording()
                }
            }
            Constants.ACTION_STOP -> stopRecording()
            Constants.ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isCapturing.set(false)
        recordingJob?.cancel()
        timerJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        unregisterHeadsetReceiver()
        unregisterPhoneStateListener()
        abandonAudioFocus()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (StorageUtils.getAvailableStorageMB(this) < Constants.MIN_STORAGE_MB) {
            showWarningNotification("Recording stopped - Low storage")
            _recordingState.value = RecordingStatus.Stopped
            stopSelf()
            return
        }

        requestAudioFocus()

        val minBufferSize = AudioRecord.getMinBufferSize(
            Constants.SAMPLE_RATE, Constants.CHANNEL_CONFIG, Constants.AUDIO_FORMAT
        )
        val bufferSize = maxOf(minBufferSize * 2, Constants.SAMPLE_RATE * Constants.BYTES_PER_SAMPLE)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            Constants.SAMPLE_RATE,
            Constants.CHANNEL_CONFIG,
            Constants.AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            showWarningNotification("Failed to initialize audio recorder")
            _recordingState.value = RecordingStatus.Stopped
            stopSelf()
            return
        }

        audioRecord?.startRecording()
        recordingStartTimeMs = System.currentTimeMillis()
        lastSoundTimeMs = recordingStartTimeMs
        isPaused = false
        chunkSequence = 0
        _recordingState.value = RecordingStatus.Recording
        _currentSessionId.value = sessionId
        _silenceDetected.value = false

        serviceScope.launch {
            recordingRepository.updateSessionStatus(sessionId, "RECORDING")
        }

        startTimer()
        startCaptureLoop()
    }

    private fun startCaptureLoop() {
        isCapturing.set(true)
        recordingJob = serviceScope.launch(Dispatchers.IO) {
            val readSize = Constants.SAMPLE_RATE / 10
            val buffer = ShortArray(readSize)
            val chunkData = ByteArrayOutputStream()
            var overlapBytes: ByteArray? = null
            var chunkStartTimeMs = System.currentTimeMillis()
            val bytesPerSecond = Constants.SAMPLE_RATE * Constants.BYTES_PER_SAMPLE
            val chunkTargetBytes = (Constants.CHUNK_DURATION_MS / 1000.0 * bytesPerSecond).toInt()
            val overlapSizeBytes = (Constants.OVERLAP_DURATION_MS / 1000.0 * bytesPerSecond).toInt()
            var storageCheckCounter = 0

            try {
                while (isCapturing.get()) {
                    if (isPaused) {
                        Thread.sleep(200)
                        continue
                    }

                    val ar = audioRecord ?: break
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        Thread.sleep(100)
                        continue
                    }

                    val readCount = ar.read(buffer, 0, buffer.size)
                    if (readCount <= 0) {
                        if (!isCapturing.get()) break
                        continue
                    }

                    val byteData = AudioUtils.shortsToBytes(buffer, readCount)
                    chunkData.write(byteData)

                    val rms = AudioUtils.calculateRms(buffer, readCount)
                    if (rms > Constants.SILENCE_THRESHOLD) {
                        lastSoundTimeMs = System.currentTimeMillis()
                        if (silenceWarningActive) {
                            silenceWarningActive = false
                            _silenceDetected.value = false
                        }
                    } else if (System.currentTimeMillis() - lastSoundTimeMs > Constants.SILENCE_DURATION_MS && !silenceWarningActive) {
                        silenceWarningActive = true
                        _silenceDetected.value = true
                        withContext(Dispatchers.Main) {
                            showWarningNotification("No audio detected - Check microphone")
                        }
                    }

                    if (chunkData.size() >= chunkTargetBytes) {
                        val allBytes = chunkData.toByteArray()
                        overlapBytes = allBytes.copyOfRange(
                            maxOf(0, allBytes.size - overlapSizeBytes), allBytes.size
                        )
                        saveChunk(allBytes, chunkStartTimeMs)
                        chunkData.reset()
                        overlapBytes?.let { chunkData.write(it) }
                        chunkStartTimeMs = System.currentTimeMillis() - Constants.OVERLAP_DURATION_MS
                    }

                    storageCheckCounter++
                    if (storageCheckCounter >= 50) {
                        storageCheckCounter = 0
                        if (StorageUtils.getAvailableStorageMB(this@AudioRecordingService) < Constants.MIN_STORAGE_MB) {
                            withContext(NonCancellable) {
                                val remaining = chunkData.toByteArray()
                                if (remaining.isNotEmpty()) saveChunk(remaining, chunkStartTimeMs)
                            }
                            withContext(Dispatchers.Main) {
                                showWarningNotification("Recording stopped - Low storage")
                            }
                            isCapturing.set(false)
                            stopRecording()
                            return@launch
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    val remaining = chunkData.toByteArray()
                    if (remaining.size > Constants.BYTES_PER_SAMPLE * 100) {
                        saveChunk(remaining, chunkStartTimeMs)
                    }
                }
            }
        }
    }

    private suspend fun saveChunk(audioData: ByteArray, startTimeMs: Long) {
        val file = File(
            StorageUtils.getAudioDir(this@AudioRecordingService),
            "session_${sessionId}_chunk_${chunkSequence}.wav"
        )
        AudioUtils.writeWavFile(file, audioData)

        val chunkId = recordingRepository.saveAudioChunk(
            sessionId = sessionId,
            filePath = file.absolutePath,
            sequenceNumber = chunkSequence,
            startTimeMs = startTimeMs,
            endTimeMs = System.currentTimeMillis()
        )

        recordingRepository.enqueueTranscription(this@AudioRecordingService, chunkId)
        chunkSequence++
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive) {
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - recordingStartTimeMs - totalPausedMs
                    _elapsedSeconds.value = maxOf(0, elapsed / 1000)
                    serviceScope.launch {
                        recordingRepository.updateSessionDuration(sessionId, elapsed)
                    }
                    withContext(Dispatchers.Main) {
                        updateNotification()
                    }
                }
                delay(1000)
            }
        }
    }

    private fun pauseRecording(reason: String) {
        if (isPaused) return
        isPaused = true
        pauseReason = reason
        pauseStartTimeMs = System.currentTimeMillis()
        try { audioRecord?.stop() } catch (_: Exception) {}

        val status = when (reason) {
            "PAUSED_PHONE_CALL" -> RecordingStatus.PausedPhoneCall
            else -> RecordingStatus.PausedAudioFocus
        }
        _recordingState.value = status

        serviceScope.launch {
            recordingRepository.updateSessionStatus(sessionId, reason)
        }
        updateNotification()
    }

    @SuppressLint("MissingPermission")
    private fun resumeRecording() {
        if (!isPaused) return
        totalPausedMs += System.currentTimeMillis() - pauseStartTimeMs
        isPaused = false
        pauseReason = ""

        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
            }
        } catch (_: Exception) {}

        _recordingState.value = RecordingStatus.Recording
        serviceScope.launch {
            recordingRepository.updateSessionStatus(sessionId, "RECORDING")
        }
        updateNotification()
    }

    private fun stopRecording() {
        isCapturing.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}

        serviceScope.launch {
            try { recordingJob?.join() } catch (_: Exception) {}
            timerJob?.cancel()

            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null

            val durationMs = System.currentTimeMillis() - recordingStartTimeMs - totalPausedMs
            recordingRepository.finishSession(sessionId, maxOf(0, durationMs))

            _recordingState.value = RecordingStatus.Stopped
            _elapsedSeconds.value = 0
            _currentSessionId.value = null
            _silenceDetected.value = false

            withContext(Dispatchers.Main) {
                abandonAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel = NotificationChannel(
                Constants.RECORDING_CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active recording notification"
                setShowBadge(false)
            }

            val warningChannel = NotificationChannel(
                Constants.WARNING_CHANNEL_ID,
                "Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recording warnings and errors"
            }

            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    private fun buildNotification(status: String, timer: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioRecordingService::class.java).apply {
                action = Constants.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, Constants.RECORDING_CHANNEL_ID)
            .setContentTitle("TwinMind Recording")
            .setContentText("$status  •  $timer")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isPaused) {
            val resumeIntent = PendingIntent.getService(
                this, 1,
                Intent(this, AudioRecordingService::class.java).apply {
                    action = Constants.ACTION_RESUME
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_play, "Resume", resumeIntent)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val elapsed = _elapsedSeconds.value
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        val timer = String.format("%02d:%02d", minutes, seconds)
        val status = _recordingState.value.displayText

        notificationManager.notify(Constants.NOTIFICATION_ID, buildNotification(status, timer))
    }

    private fun showWarningNotification(message: String) {
        val notification = NotificationCompat.Builder(this, Constants.WARNING_CHANNEL_ID)
            .setContentTitle("TwinMind")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_mic)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(Constants.WARNING_NOTIFICATION_ID, notification)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerPhoneStateListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(
                    phoneStateListener,
                    android.telephony.PhoneStateListener.LISTEN_CALL_STATE
                )
            }
        } catch (_: SecurityException) {}
    }

    private fun unregisterPhoneStateListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(
                    phoneStateListener,
                    android.telephony.PhoneStateListener.LISTEN_NONE
                )
            }
        } catch (_: Exception) {}
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isPaused) pauseRecording("PAUSED_PHONE_CALL")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isPaused && pauseReason == "PAUSED_PHONE_CALL") resumeRecording()
            }
        }
    }

    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(headsetReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(headsetReceiver, filter)
        }
    }

    private fun unregisterHeadsetReceiver() {
        try { unregisterReceiver(headsetReceiver) } catch (_: Exception) {}
    }

    companion object {
        private val _recordingState = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
        val recordingState: StateFlow<RecordingStatus> = _recordingState

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId

        private val _silenceDetected = MutableStateFlow(false)
        val silenceDetected: StateFlow<Boolean> = _silenceDetected
    }
}
