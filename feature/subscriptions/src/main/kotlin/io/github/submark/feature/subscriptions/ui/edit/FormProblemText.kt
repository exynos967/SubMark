package io.github.submark.feature.subscriptions.ui.edit

import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.subscriptions.R

fun FormProblem.toUiText(): UiText = when (this) {
    FormProblem.BlankName -> UiText.res(R.string.subscriptions_edit_error_name)
    is FormProblem.Price -> UiText.res(error.messageRes)
    FormProblem.CustomCycle -> UiText.res(R.string.subscriptions_edit_error_custom_cycle)
    FormProblem.TrialStart -> UiText.res(R.string.subscriptions_edit_error_trial_start)
    FormProblem.TrialDays -> UiText.res(R.string.subscriptions_edit_error_trial_days)
    FormProblem.EndNotAfterStart -> UiText.res(R.string.subscriptions_edit_error_end_after_start)
    FormProblem.SingleCycleNeedsEnd -> UiText.res(R.string.subscriptions_edit_error_single_cycle_end)
    FormProblem.Deposit -> UiText.res(R.string.subscriptions_edit_error_deposit)
    FormProblem.BundleNeedsChild -> UiText.res(R.string.subscriptions_edit_error_bundle_child)
    is FormProblem.ChildBlankName -> UiText.res(R.string.subscriptions_edit_error_child_name, index + 1)
    is FormProblem.ChildPrice -> UiText.res(R.string.subscriptions_edit_error_child_price, index + 1)
    is FormProblem.ChildCustomCycle -> UiText.res(R.string.subscriptions_edit_error_child_cycle, index + 1)
    is FormProblem.ChildEndNotAfterStart -> UiText.res(R.string.subscriptions_edit_error_child_end, index + 1)
    is FormProblem.FieldRequired -> UiText.res(R.string.subscriptions_edit_error_field_required, name)
    is FormProblem.FieldInvalid -> UiText.res(R.string.subscriptions_edit_error_field_invalid, name)
}
