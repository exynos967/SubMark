package io.github.submark.core.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.AppSettingsSerializer
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.data.settings.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalTime

class AppSettingsSerializerTest {
    private val serializer = AppSettingsSerializer(AppSettings())

    @Test fun roundTrip() = runTest {
        val settings = AppSettings(
            display = AppSettings().display.copy(theme = ThemeMode.DARK),
            money = AppSettings().money.copy(annualBudget = BigDecimal("1200.50")),
            notifications = AppSettings().notifications.copy(firstTime = LocalTime.of(8, 30)),
        )
        val out = ByteArrayOutputStream()
        serializer.writeTo(settings, out)
        assertThat(serializer.readFrom(ByteArrayInputStream(out.toByteArray()))).isEqualTo(settings)
    }

    @Test fun missingAndUnknownFieldsFallBackToDefaults() = runTest {
        val json = """{"display":{"theme":"LIGHT","futureField":1},"unknownSection":{}}"""
        val read = serializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertThat(read.display.theme).isEqualTo(ThemeMode.LIGHT)
        assertThat(read.money).isEqualTo(AppSettings().money)
    }

    /** Files written before the classic overview layout was removed still load, keeping the modern settings. */
    @Test fun legacyClassicOverviewFieldsAreIgnored() = runTest {
        val json = """{"overview":{"layout":"CLASSIC","period":"YEAR",""" +
            """"classicComponents":[{"id":"TREND","visible":false}],""" +
            """"modernComponents":[{"id":"COMING_UP","visible":false}]}}"""
        val read = serializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertThat(read.overview.period).isEqualTo(SummaryPeriod.YEAR)
        assertThat(read.overview.modernComponents)
            .containsExactly(ComponentSetting(ModernOverviewComponent.COMING_UP, visible = false))
    }
}
