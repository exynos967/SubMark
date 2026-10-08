package io.github.submark.feature.backup.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.feature.backup.R
import io.github.submark.core.model.BackupProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the one-off "backup failed" notification on channel [CHANNEL_ID] ("backup_status", shared
 * with the notifications feature when it ships). Creates the channel if absent.
 */
@Singleton
class BackupNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.backup_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.backup_channel_description) }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyFailure(profile: BackupProfile, error: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.backup_failed_title))
            .setContentText(context.getString(R.string.backup_failed_body, profile.name))
            .setSubText(error)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(profile.id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "backup_status"
    }
}
