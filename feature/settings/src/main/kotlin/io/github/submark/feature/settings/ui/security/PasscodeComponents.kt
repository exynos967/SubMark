package io.github.submark.feature.settings.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.security.PasscodeHasher
import kotlin.math.roundToInt

/** Four dots showing how many digits are entered; shakes whenever [shakeKey] changes (wrong code). */
@Composable
internal fun PasscodeDots(filled: Int, shakeKey: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 400
                -24f at 50
                24f at 120
                -16f at 190
                16f at 260
                -6f at 330
            },
        )
    }
    val description = stringResource(R.string.settings_passcode_progress, filled, PasscodeHasher.LENGTH)
    Row(
        modifier = modifier
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        repeat(PasscodeHasher.LENGTH) { i ->
            Box(
                Modifier
                    .size(16.dp)
                    .border(2.dp, color, CircleShape)
                    .then(if (i < filled) Modifier.background(color, CircleShape) else Modifier),
            )
        }
    }
}

/**
 * Numeric keypad. The bottom-left key shows a fingerprint button when [onBiometric] is set.
 */
@Composable
internal fun PasscodeKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { d -> DigitKey(d, enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) {
                if (onBiometric != null) {
                    IconButton(onClick = onBiometric, modifier = Modifier.size(KeySize)) {
                        Icon(Icons.Rounded.Fingerprint, stringResource(R.string.settings_lock_use_biometric), Modifier.size(32.dp))
                    }
                }
            }
            DigitKey('0', enabled) { onDigit('0') }
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(KeySize)) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.settings_passcode_delete_digit))
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KeySize)) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

private val KeySize = 72.dp
