package com.project1.psira

import android.media.MediaPlayer

object AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(context: android.content.Context, url: String, onStart: () -> Unit, onError: () -> Unit) {
        try {
            stop()
            var dataSource = url
            
            // Handle Base64 Data URI
            if (url.startsWith("data:audio")) {
                val base64Data = url.substringAfter("base64,")
                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                val tempFile = java.io.File(context.cacheDir, "temp_audio_play.m4a")
                tempFile.writeBytes(decodedBytes)
                dataSource = tempFile.absolutePath
            }

            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(dataSource)
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
