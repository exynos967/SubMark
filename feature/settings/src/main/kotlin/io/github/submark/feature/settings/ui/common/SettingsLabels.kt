package io.github.submark.feature.settings.ui.common

import androidx.annotation.StringRes
import io.github.submark.core.data.settings.AppIcon
import io.github.submark.core.data.settings.AppLanguage
import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.DefaultListStyle
import io.github.submark.core.data.settings.FloatingTabWidth
import io.github.submark.core.data.settings.FontFamilyOption
import io.github.submark.core.data.settings.FontSize
import io.github.submark.core.data.settings.FontTheme
import io.github.submark.core.data.settings.PopularRegionScope
import io.github.submark.core.data.settings.StartupTab
import io.github.submark.core.data.settings.ThemeMode
import io.github.submark.feature.settings.R

@get:StringRes
internal val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

/** Language names are shown in their own language except "follow system". */
@get:StringRes
internal val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_zh_cn
    }

@get:StringRes
internal val FontTheme.labelRes: Int
    get() = when (this) {
        FontTheme.MODERN -> R.string.settings_font_theme_modern
        FontTheme.COMFORTABLE -> R.string.settings_font_theme_comfortable
        FontTheme.COMPACT -> R.string.settings_font_theme_compact
        FontTheme.ELEGANT -> R.string.settings_font_theme_elegant
        FontTheme.ACCESSIBLE -> R.string.settings_font_theme_accessible
        FontTheme.CUSTOM -> R.string.settings_font_theme_custom
    }

@get:StringRes
internal val FontTheme.descriptionRes: Int
    get() = when (this) {
        FontTheme.MODERN -> R.string.settings_font_theme_modern_desc
        FontTheme.COMFORTABLE -> R.string.settings_font_theme_comfortable_desc
        FontTheme.COMPACT -> R.string.settings_font_theme_compact_desc
        FontTheme.ELEGANT -> R.string.settings_font_theme_elegant_desc
        FontTheme.ACCESSIBLE -> R.string.settings_font_theme_accessible_desc
        FontTheme.CUSTOM -> R.string.settings_font_theme_custom_desc
    }

@get:StringRes
internal val FontFamilyOption.labelRes: Int
    get() = when (this) {
        FontFamilyOption.SYSTEM -> R.string.settings_font_family_system
        FontFamilyOption.ROUNDED -> R.string.settings_font_family_rounded
        FontFamilyOption.SERIF -> R.string.settings_font_family_serif
        FontFamilyOption.MONOSPACE -> R.string.settings_font_family_monospace
        FontFamilyOption.CJK_OPTIMIZED -> R.string.settings_font_family_cjk
    }

@get:StringRes
internal val FontSize.labelRes: Int
    get() = when (this) {
        FontSize.SMALL -> R.string.settings_font_size_small
        FontSize.MEDIUM -> R.string.settings_font_size_medium
        FontSize.LARGE -> R.string.settings_font_size_large
        FontSize.EXTRA_LARGE -> R.string.settings_font_size_extra_large
    }

@get:StringRes
internal val StartupTab.labelRes: Int
    get() = when (this) {
        StartupTab.OVERVIEW -> R.string.settings_tab_overview
        StartupTab.SUBSCRIPTIONS -> R.string.settings_tab_subscriptions
        StartupTab.CALENDAR -> R.string.settings_tab_calendar
        StartupTab.ANALYTICS -> R.string.settings_tab_analytics
    }

@get:StringRes
internal val FloatingTabWidth.labelRes: Int
    get() = when (this) {
        FloatingTabWidth.NARROW -> R.string.settings_tab_width_narrow
        FloatingTabWidth.COMPACT -> R.string.settings_tab_width_compact
        FloatingTabWidth.STANDARD -> R.string.settings_tab_width_standard
        FloatingTabWidth.COMFORTABLE -> R.string.settings_tab_width_comfortable
        FloatingTabWidth.WIDE -> R.string.settings_tab_width_wide
    }

@get:StringRes
internal val DefaultListStyle.labelRes: Int
    get() = when (this) {
        DefaultListStyle.LIST -> R.string.settings_list_style_list
        DefaultListStyle.GRID -> R.string.settings_list_style_grid
        DefaultListStyle.LAST_USED -> R.string.settings_list_style_last_used
    }

@get:StringRes
internal val CalendarMode.labelRes: Int
    get() = when (this) {
        CalendarMode.MONTH -> R.string.settings_calendar_mode_month
        CalendarMode.WEEK -> R.string.settings_calendar_mode_week
        CalendarMode.TIMELINE -> R.string.settings_calendar_mode_timeline
    }

@get:StringRes
internal val PopularRegionScope.labelRes: Int
    get() = when (this) {
        PopularRegionScope.ALL -> R.string.settings_region_scope_all
        PopularRegionScope.LOCAL -> R.string.settings_region_scope_local
    }

@get:StringRes
internal val AppIcon.labelRes: Int
    get() = when (this) {
        AppIcon.DEFAULT -> R.string.settings_app_icon_default
        AppIcon.STYLE_1 -> R.string.settings_app_icon_style_1
        AppIcon.STYLE_2 -> R.string.settings_app_icon_style_2
        AppIcon.STYLE_3 -> R.string.settings_app_icon_style_3
        // Styles 4-6 exist in the settings enum for compatibility but have no launcher alias.
        AppIcon.STYLE_4, AppIcon.STYLE_5, AppIcon.STYLE_6 -> R.string.settings_app_icon_default
    }
