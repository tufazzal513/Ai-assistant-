package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    private val queue = ConcurrentLinkedQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    init {
        val sampleRate = 24000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
        
        scope.launch {
            while (isActive) {
                if (queue.isNotEmpty()) {
                    if (!_isPlaying.value) _isPlaying.value = true
                    val data = queue.poll()
                    if (data != null) {
                        audioTrack?.write(data, 0, data.size)
                    }
                } else {
                    if (_isPlaying.value) _isPlaying.value = false
                    Thread.sleep(10)
                }
            }
        }
    }

    fun play(pcmData: ByteArray) {
        queue.add(pcmData)
    }
    
    fun playTestTone() {
        // Generate a 440Hz sine wave tone for 1 second at 24000Hz
        val sampleRate = 24000
        val duration = 1.0 // seconds
        val numSamples = (sampleRate * duration).toInt()
        val sample = ByteArray(numSamples * 2) // 16-bit PCM
        for (i in 0 until numSamples) {
            val value = Math.sin(2.0 * Math.PI * i / (sampleRate / 440.0))
            val shortVal = (value * Short.MAX_VALUE).toInt().toShort()
            sample[i * 2] = (shortVal.toInt() and 0x00FF).toByte()
            sample[i * 2 + 1] = ((shortVal.toInt() and 0xFF00) shr 8).toByte()
        }
        play(sample)
    }

    fun stop() {
        queue.clear()
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        _isPlaying.value = false
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
