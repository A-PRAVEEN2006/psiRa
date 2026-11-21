package com.project1.psira

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File

class MediaVaultViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ITEM_ID       = "vault_item_id"
        const val EXTRA_MIME          = "vault_mime"
        const val EXTRA_ENC_FILENAME  = "vault_enc_filename"
        const val EXTRA_ORIGINAL_NAME = "vault_original_name"
    }

    private var tempFile: File? = null
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var videoView: VideoView? = null
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_media_vault_viewer)

        val itemId       = intent.getStringExtra(EXTRA_ITEM_ID) ?: return finish()
        val mimeType     = intent.getStringExtra(EXTRA_MIME) ?: "image/jpeg"
        val encFileName  = intent.getStringExtra(EXTRA_ENC_FILENAME) ?: return finish()
        val originalName = intent.getStringExtra(EXTRA_ORIGINAL_NAME) ?: "Media"

        val imgView       = findViewById<ImageView>(R.id.imgVaultViewer)
        val videoContainer= findViewById<View>(R.id.videoContainer)
        val progress      = findViewById<ProgressBar>(R.id.progressVaultViewer)
        val tvName        = findViewById<TextView>(R.id.tvViewerFileName)
        val btnBack       = findViewById<ImageButton>(R.id.btnVaultBack)
        val btnPlay       = findViewById<ImageView>(R.id.btnPlayPause)
        videoView         = findViewById(R.id.videoVaultViewer)

        tvName.text = originalName
        btnBack.setOnClickListener { finish() }

        // Build a minimal VaultMediaItem just to call decryptToTemp
        val item = VaultMediaItem(
            id = itemId,
            mimeType = mimeType,
            encFileName = encFileName,
            originalName = originalName
        )

        progress.visibility = View.VISIBLE

        uiScope.launch {
            val file = withContext(Dispatchers.IO) {
                try { MediaVaultManager.decryptToTemp(this@MediaVaultViewerActivity, item) }
                catch (e: Exception) { null }
            }
            progress.visibility = View.GONE

            if (file == null) {
                Toast.makeText(this@MediaVaultViewerActivity, "Failed to decrypt file", Toast.LENGTH_LONG).show()
                return@launch
            }
            tempFile = file

            if (mimeType.startsWith("image/")) {
                imgView.visibility = View.VISIBLE
                val bmp = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                imgView.setImageBitmap(bmp)
                setupPinchZoom(imgView)
            } else if (mimeType.startsWith("video/")) {
                videoContainer.visibility = View.VISIBLE
                val uri = Uri.fromFile(file)
                videoView?.setVideoURI(uri)
                videoView?.setOnPreparedListener { mp ->
                    mp.isLooping = false
                    btnPlay.visibility = View.VISIBLE
                }
                videoView?.setOnCompletionListener {
                    isPlaying = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    btnPlay.visibility = View.VISIBLE
                }

                btnPlay.setOnClickListener {
                    if (isPlaying) {
                        videoView?.pause()
                        isPlaying = false
                        btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        videoView?.start()
                        isPlaying = true
                        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                        btnPlay.visibility = View.GONE
                    }
                }

                videoContainer.setOnClickListener {
                    if (isPlaying) {
                        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                        btnPlay.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupPinchZoom(imageView: ImageView) {
        var scaleFactor = 1f
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
                imageView.scaleX = scaleFactor
                imageView.scaleY = scaleFactor
                return true
            }
        })
        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
    }

    override fun onStop() {
        super.onStop()
        videoView?.pause()
        isPlaying = false
        // Delete temp file when user leaves
        tempFile?.delete()
        tempFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView?.stopPlayback()
        uiScope.cancel()
        tempFile?.delete()
        tempFile = null
    }
}
