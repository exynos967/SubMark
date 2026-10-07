package io.github.submark.core.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App metadata that library modules cannot get from BuildConfig. */
@Singleton
class AppInfo @Inject constructor(@ApplicationContext context: Context) {
    val versionName: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
}
