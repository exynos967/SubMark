package io.github.submark.feature.settings.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.settings.AppIcon
import io.github.submark.feature.settings.R
import javax.inject.Inject
import javax.inject.Singleton

/** Launcher icon style backed by one `<activity-alias>` in this module's manifest. */
enum class LauncherIcon(
    val setting: AppIcon,
    val aliasClass: String,
    val enabledByDefault: Boolean,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    DEFAULT(AppIcon.DEFAULT, "io.github.submark.LauncherDefault", true, R.drawable.settings_icon_default_bg, R.drawable.settings_icon_default_fg),
    STYLE_1(AppIcon.STYLE_1, "io.github.submark.LauncherStyle1", false, R.drawable.settings_icon_style1_bg, R.drawable.settings_icon_style1_fg),
    STYLE_2(AppIcon.STYLE_2, "io.github.submark.LauncherStyle2", false, R.drawable.settings_icon_style2_bg, R.drawable.settings_icon_style2_fg),
    STYLE_3(AppIcon.STYLE_3, "io.github.submark.LauncherStyle3", false, R.drawable.settings_icon_style3_bg, R.drawable.settings_icon_style3_fg),
    ;

    companion object {
        fun of(setting: AppIcon): LauncherIcon = entries.firstOrNull { it.setting == setting } ?: DEFAULT
    }
}

/** Switches the launcher alias. The launcher may need a moment (or a restart) to pick the change up. */
@Singleton
class AppIconManager @Inject constructor(@ApplicationContext private val context: Context) {

    /** The alias that is currently enabled (manifest defaults applied). */
    fun current(): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.enabledByDefault
                else -> false
            }
        } ?: LauncherIcon.DEFAULT
    }

    /** Enables [icon] first, then disables the others, so there is always a launcher entry. */
    fun apply(icon: LauncherIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        LauncherIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun component(icon: LauncherIcon) = ComponentName(context.packageName, icon.aliasClass)
}
