package com.project1.psira

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase
import java.io.File

class UpdateManager(private val activity: Activity) {

    fun checkUpdate(isManual: Boolean = false) {
        if (isManual) {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show()
        }
        val databaseRef = FirebaseDatabase.getInstance().getReference("app_update")
        databaseRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val serverVersionCode = snapshot.child("versionCode").getValue(Long::class.java)?.toInt() ?: 0
                val apkUrl = snapshot.child("apkUrl").getValue(String::class.java) ?: ""
                val changelog = snapshot.child("changelog").getValue(String::class.java) ?: ""
                val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false

                val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (serverVersionCode > currentVersionCode) {
                    showUpdateDialog(apkUrl, changelog, forceUpdate)
                } else {
                    if (isManual) {
                        Toast.makeText(activity, "App is up to date (v${packageInfo.versionName})", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                if (isManual) {
                    Toast.makeText(activity, "No update information found.", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (isManual) {
                Toast.makeText(activity, "Failed to check for updates: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(apkUrl: String, changelog: String, forceUpdate: Boolean) {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("New Update Available")
            .setMessage("What's new:\n$changelog")
            .setCancelable(!forceUpdate)
            .setPositiveButton("Update Now") { _, _ ->
                checkInstallPermissionAndDownload(apkUrl)
            }

        if (!forceUpdate) {
            dialog.setNegativeButton("Later", null)
        }

        dialog.show()
    }

    private fun checkInstallPermissionAndDownload(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow unknown app installation for PsiRa to update.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            }
        }
        startApkDownload(apkUrl)
    }

    private fun startApkDownload(apkUrl: String) {
        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val request = android.app.DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading PsiRa Update")
            setDescription("Fetching latest release...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "update.apk")
        }

        // Clean up old file first if it exists
        val oldFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (oldFile.exists()) {
            oldFile.delete()
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                    if (file.exists()) {
                        installApk(file)
                    }
                    activity.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                onComplete,
                IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            activity.registerReceiver(
                onComplete,
                IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(file: File) {
        val apkUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        activity.startActivity(intent)
    }
}
