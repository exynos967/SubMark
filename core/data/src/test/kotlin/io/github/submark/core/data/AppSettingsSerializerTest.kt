package io.github.submark.core.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.AppSettingsSerializer
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
}
