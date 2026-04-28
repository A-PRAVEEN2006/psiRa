package com.project1.psira

import android.media.MediaPlayer

object AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onStart: () -> Unit, onError: () -> Unit) {
        try {
            stop()
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.setOnPreparedListener { 
                it.start()
                onStart()
            }
            mediaPlayer?.setOnCompletionListener { 
                stop()
            }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                onError()
                stop()
                true
            }
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            onError()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
        }
    }
}
