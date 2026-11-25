package com.gasser.fajr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class SoundPlayer(ctx: Context) {
    private val h = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var enhancer: LoudnessEnhancer? = null
    private var tick: Runnable? = null

    fun start(kind: Int, vol: Float, boostDb: Int, rampSec: Int) {
        stop()
        val data = Synth.gen(kind)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(Synth.SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs).setAudioFormat(fmt)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2).build()
        t.write(data, 0, data.size)
        t.setLoopPoints(0, data.size, -1)
        try {
            enhancer = LoudnessEnhancer(t.audioSessionId).apply {
                setTargetGain(boostDb * 100)
                setEnabled(true)
            }
        } catch (_: Exception) {
        }
        val begin = SystemClock.elapsedRealtime()
        fun level(): Float {
            if (rampSec <= 0) return vol
            val el = (SystemClock.elapsedRealtime() - begin) / 1000f
            return vol * (0.25f + 0.75f * minOf(1f, el / rampSec))
        }
        t.setVolume(level())
        t.play()
        track = t

        val r = object : Runnable {
            override fun run() {
                track?.setVolume(level())
                h.postDelayed(this, 300)
            }
        }
        tick = r
        h.postDelayed(r, 300)
    }

    fun stop() {
        tick?.let { h.removeCallbacks(it) }
        tick = null
        try { track?.stop() } catch (_: Exception) {}
        track?.release()
        track = null
        try { enhancer?.release() } catch (_: Exception) {}
        enhancer = null
    }
}
