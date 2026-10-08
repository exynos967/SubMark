package io.github.submark.feature.share.ui.poster

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.Category
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.CycleLabels
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.share.R
import java.time.LocalDate
import java.time.ZoneId

/**
 * Static, self-contained share poster. Everything is laid out at poster scale so the same
 * composable renders correctly when drawn to a Bitmap (no MaterialTheme dependencies beyond
 * typography defaults that scale with density).
 */
@Composable
fun SharePoster(
    subscription: Subscription,
    category: Category?,
    childrenCount: Int,
    sharerName: String,
    description: String,
    style: io.github.submark.core.data.settings.PosterStyle,
    qrBitmap: Bitmap?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val accent = colorFromHex(category?.colorHex) ?: FallbackAccent
    val palette = posterPalette(style, accent)
    val created = LocalDate.ofInstant(subscription.createdAt, ZoneId.systemDefault())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubscriptionIcon(
                type = subscription.iconType,
                value = subscription.iconValue,
                fallbackName = subscription.name,
                size = 72.dp,
            )
            Text(
                subscription.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosterTag(typeLabel(subscription), palette.onBackground)
                if (subscription.renewalType == RenewalType.AUTO && subscription.kind != SubscriptionKind.LIFETIME) {
                    PosterTag(
                        stringResource(R.string.share_badge_auto_renew).uppercase(),
                        palette.onBackground,
                    )
                }
                if (subscription.kind == SubscriptionKind.LIFETIME) {
                    PosterTag(stringResource(R.string.share_badge_lifetime).uppercase(), palette.onBackground)
                }
            }

            Text(
                formatMoney(subscription.price, subscription.currencyCode),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
            )
            Text(
                CycleLabels.label(subscription).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackground.copy(alpha = 0.7f),
            )
            category?.let { cat ->
                Text(
                    cat.displayName().asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackground.copy(alpha = 0.7f),
                )
            }
            if (childrenCount > 0) {
                Text(
                    pluralStringResource(R.plurals.share_bundle_children, childrenCount, childrenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onBackground.copy(alpha = 0.7f),
                )
            }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onBackground.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (sharerName.isNotBlank()) {
                Text(
                    stringResource(R.string.share_shared_by, sharerName),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.onBackground,
                )
            }

            if (qrBitmap != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_scan_hint),
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.share_scan_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onBackground.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.share_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.share_created_on, DateLabels.formatDate(created)),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun PosterTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun typeLabel(sub: Subscription): String = stringResource(
    when {
        sub.kind == SubscriptionKind.LIFETIME -> R.string.share_type_lifetime
        sub.kind == SubscriptionKind.STORED_VALUE -> R.string.share_type_stored_value
        sub.bundleRole == BundleRole.MAIN -> R.string.share_type_bundle
        sub.bundleRole == BundleRole.CHILD -> R.string.share_type_child
        sub.isShared -> R.string.share_type_shared
        else -> R.string.share_type_regular
    },
)

/** Poster is square-ish; the bitmap renderer uses a fixed pixel size. */
val PosterWidth: Dp = 360.dp
val PosterHeight: Dp = 640.dp
