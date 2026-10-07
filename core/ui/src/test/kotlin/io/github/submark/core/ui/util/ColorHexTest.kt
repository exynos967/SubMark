package io.github.submark.core.ui.util

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorHexTest {
    @Test fun `parses common forms`() {
        assertThat(colorFromHex("#FF0000")).isEqualTo(Color(0xFFFF0000))
        assertThat(colorFromHex("00ff00")).isEqualTo(Color(0xFF00FF00))
        assertThat(colorFromHex("#00F")).isEqualTo(Color(0xFF0000FF))
        assertThat(colorFromHex("#800000FF")).isEqualTo(Color(0x800000FF))
    }

    @Test fun `rejects invalid`() {
        assertThat(colorFromHex(null)).isNull()
        assertThat(colorFromHex("")).isNull()
        assertThat(colorFromHex("#GG0000")).isNull()
        assertThat(colorFromHex("#12345")).isNull()
    }

    @Test fun `round trips`() {
        assertThat(Color(0xFF3E63DD).toHex()).isEqualTo("#3E63DD")
        assertThat(Color(0x803E63DD).toHex()).isEqualTo("#803E63DD")
        assertThat(colorFromHex(Color(0xFF12A594).toHex())).isEqualTo(Color(0xFF12A594))
    }

    @Test fun `content color`() {
        assertThat(Color.White.contentColorFor()).isEqualTo(Color.Black)
        assertThat(Color(0xFF1C1C21).contentColorFor()).isEqualTo(Color.White)
    }
}
