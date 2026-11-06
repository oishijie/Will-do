package com.antgskds.calendarassistant.feature.quickmemo.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QuickMemoRecordingResult(
    val path: String,
    val durationMs: Long
)

class QuickMemoAudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var audioRecordSession: AudioRecordSession? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    /** 实时电平（峰值振幅），0f..1f。录音时由写盘线程 / MediaRecorder 轮询更新。 */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private var amplitudePollJob: Job? = null
    private val amplitudeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val isRecording: Boolean get() = recorder != null || audioRecordSession != null

    fun start(): String {
        stopAndDiscard()
        val audioDir = File(context.filesDir, AUDIO_DIR).apply { mkdirs() }
        val timestamp = fileNameFormatter.format(Date())
        val name = "quick_memo_$timestamp.m4a"
        val file = File(audioDir, name)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(48_000)
            mediaRecorder.setAudioSamplingRate(16_000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()

            recorder = mediaRecorder
            outputFile = file
            startedAt = System.currentTimeMillis()
            startMediaRecorderAmplitudePolling(mediaRecorder)
            return file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder start failed, falling back to AudioRecord", e)
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
            runCatching { file.delete() }
            return try {
                startAudioRecord(File(audioDir, "quick_memo_$timestamp.wav"))
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(e)
                throw fallbackError
            }
        }
    }

    fun stop(): QuickMemoRecordingResult? {
        val audioRecordSession = audioRecordSession
        if (audioRecordSession != null) return stopAudioRecord(audioRecordSession, delete = false)
        val mediaRecorder = recorder ?: return null
        val file = outputFile
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        recorder = null
        outputFile = null
        startedAt = 0L

        runCatching { mediaRecorder.stop() }
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
        stopAmplitudePolling()

        if (file == null || !file.exists()) return null
        return QuickMemoRecordingResult(file.absolutePath, duration)
    }

    fun stopAndDiscard() {
        val file = outputFile
        val mediaRecorder = recorder
        val audioRecordSession = audioRecordSession
        recorder = null
        this.audioRecordSession = null
        outputFile = null
        startedAt = 0L
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
        }
        stopAmplitudePolling()
        if (audioRecordSession != null) stopAudioRecord(audioRecordSession, delete = true)
        if (file != null) runCatching { file.delete() }
    }

    private fun startAudioRecord(file: File): String {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferSize > 0) { "AudioRecord buffer unavailable" }
        val bufferSize = max(minBufferSize, SAMPLE_RATE)
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord.release() }
            throw IllegalStateException("AudioRecord init failed")
        }

        val output = RandomAccessFile(file, "rw")
        writeWavHeader(output, 0L)
        val session = AudioRecordSession(
            audioRecord = audioRecord,
            output = output,
            file = file,
            running = AtomicBoolean(true)
        )
        val buffer = ByteArray(bufferSize)
        session.thread = Thread({
            while (session.running.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(session) {
                        output.write(buffer, 0, read)
                        session.bytesWritten += read.toLong()
                    }
                    // 实时电平：取本块 PCM16 的峰值，供录音胶囊波形用。
                    // 在写盘线程里顺带算，不另起线程。
                    publishAmplitude(peakAmplitudeOf(buffer, read))
                }
            }
        }, "QuickMemoAudioRecord")
        try {
            audioRecord.startRecording()
            session.thread?.start()
            audioRecordSession = session
            outputFile = file
            startedAt = System.currentTimeMillis()
            return file.absolutePath
        } catch (e: Exception) {
            session.running.set(false)
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { output.close() }
            runCatching { file.delete() }
            throw e
        }
    }

    /** 把 PCM16 单声道的峰值振幅归一化到 0f..1f。 */
    private fun peakAmplitudeOf(buffer: ByteArray, length: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < length) {
            // PCM16 小端
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            peak = max(peak, abs(sample))
            i += 2
        }
        return (peak / Short.MAX_VALUE.toFloat()).coerceIn(0f, 1f)
    }

    private fun publishAmplitude(value: Float) {
        _amplitude.value = value
    }

    /**
     * MediaRecorder 主路径拿不到 PCM，只能轮询 maxAmplitude。
     * 每 60ms 一次，与波形帧率对齐。
     */
    private fun startMediaRecorderAmplitudePolling(recorder: MediaRecorder) {
        amplitudePollJob?.cancel()
        amplitudePollJob = amplitudeScope.launch {
            while (isActive) {
                val amp = runCatching { recorder.maxAmplitude }.getOrDefault(0)
                publishAmplitude((amp / 32767f).coerceIn(0f, 1f))
                delay(60)
            }
        }
    }

    private fun stopAmplitudePolling() {
        amplitudePollJob?.cancel()
        amplitudePollJob = null
        publishAmplitude(0f)
    }

    private fun stopAudioRecord(session: AudioRecordSession, delete: Boolean): QuickMemoRecordingResult? {
        stopAmplitudePolling()
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        if (audioRecordSession === session) audioRecordSession = null
        outputFile = null
        startedAt = 0L
        session.running.set(false)
        runCatching { session.audioRecord.stop() }
        runCatching { session.thread?.join(800L) }
        runCatching { session.audioRecord.release() }
        synchronized(session) {
            runCatching { writeWavHeader(session.output, session.bytesWritten) }
            runCatching { session.output.close() }
        }
        if (delete) {
            runCatching { session.file.delete() }
            return null
        }
        if (!session.file.exists() || session.bytesWritten <= 0L) return null
        return QuickMemoRecordingResult(session.file.absolutePath, duration)
    }

    private fun writeWavHeader(output: RandomAccessFile, dataSize: Long) {
        output.seek(0L)
        output.writeBytes("RIFF")
        writeLittleEndianInt(output, (36L + dataSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        writeLittleEndianInt(output, 16)
        writeLittleEndianShort(output, 1)
        writeLittleEndianShort(output, 1)
        writeLittleEndianInt(output, SAMPLE_RATE)
        writeLittleEndianInt(output, SAMPLE_RATE * BYTES_PER_SAMPLE)
        writeLittleEndianShort(output, BYTES_PER_SAMPLE.toShort().toInt())
        writeLittleEndianShort(output, 16)
        output.writeBytes("data")
        writeLittleEndianInt(output, dataSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        output.seek(HEADER_SIZE + dataSize)
    }

    private fun writeLittleEndianInt(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
        output.write(value shr 16 and 0xFF)
        output.write(value shr 24 and 0xFF)
    }

    private fun writeLittleEndianShort(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
    }

    private class AudioRecordSession(
        val audioRecord: AudioRecord,
        val output: RandomAccessFile,
        val file: File,
        val running: AtomicBoolean,
        var thread: Thread? = null,
        var bytesWritten: Long = 0L
    )

    companion object {
        private const val TAG = "QuickMemoAudioRecorder"
        const val MIN_RECORDING_MS = 500L
        private const val AUDIO_DIR = "quick_memos/audio"
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val HEADER_SIZE = 44L
        private val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
