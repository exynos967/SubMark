package io.github.submark.core.ui.format

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.Category
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SplitMode
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SystemCategory
import io.github.submark.core.model.TagColor
import io.github.submark.core.model.WalletKind
import io.github.submark.core.ui.R
import io.github.submark.core.ui.icon.IconCatalog
import io.github.submark.core.ui.util.colorFromHex
import org.junit.Test

class MappersTest {
    private fun <T> assertDistinct(values: List<T>) = assertThat(values.toSet()).hasSize(values.size)

    @Test fun `every enum maps to a distinct label`() {
        assertDistinct(SubscriptionKind.entries.map { it.labelRes })
        assertDistinct(RenewalType.entries.map { it.labelRes })
        assertDistinct(PaymentStatus.entries.map { it.labelRes })
        assertDistinct(PaymentKind.entries.map { it.labelRes })
        assertDistinct(PaymentSource.entries.map { it.labelRes })
        assertDistinct(MarkTiming.entries.map { it.labelRes })
        assertDistinct(SplitMode.entries.map { it.labelRes })
        assertDistinct(SplitMode.entries.map { it.descriptionRes })
        assertDistinct(WalletKind.entries.map { it.labelRes })
        assertDistinct(SystemCategory.entries.map { it.labelRes })
        assertDistinct(BillingCycle.entries.map { it.labelRes })
        assertDistinct(TagColor.entries.map { it.labelRes })
    }

    @Test fun `payment status tones`() {
        assertThat(PaymentStatus.SUCCESS.tone).isEqualTo(BadgeTone.SUCCESS)
        assertThat(PaymentStatus.FAILED.tone).isEqualTo(BadgeTone.ERROR)
    }

    @Test fun `system category defaults are valid`() {
        SystemCategory.entries.forEach {
            assertThat(IconCatalog[it.defaultIconName]).isNotNull()
            assertThat(colorFromHex(it.defaultColorHex)).isNotNull()
        }
    }

    @Test fun `tag colors differ per mode and are opaque`() {
        TagColor.entries.forEach {
            assertThat(it.color(false).alpha).isEqualTo(1f)
            assertThat(it.color(true)).isNotEqualTo(it.color(false))
        }
        assertDistinct(TagColor.entries.map { it.color(false) })
    }

    @Test fun `category display name prefers user name`() {
        val preset = Category(systemKey = SystemCategory.MUSIC, iconValue = "music", colorHex = "#000")
        assertThat(preset.displayName()).isEqualTo(UiText.res(R.string.ui_category_music))
        assertThat(preset.copy(name = "Tunes").displayName()).isEqualTo(UiText.raw("Tunes"))
    }
}
