package io.github.submark.feature.settings.ui.customization

import androidx.annotation.StringRes
import io.github.submark.core.data.settings.AddFormSettings
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.feature.settings.R

/** One boolean preference shown as a switch row. */
internal class ToggleSpec(
    @StringRes val title: Int,
    @StringRes val description: Int? = null,
    val get: (AppSettings) -> Boolean,
    val set: (AppSettings, Boolean) -> AppSettings,
)

internal enum class AddFormPreset { FULL, MINIMAL, CUSTOM }

internal object CustomizationSpecs {

    fun presetOf(form: AddFormSettings): AddFormPreset = when (form) {
        AddFormSettings.FULL -> AddFormPreset.FULL
        AddFormSettings.MINIMAL -> AddFormPreset.MINIMAL
        else -> AddFormPreset.CUSTOM
    }

    private fun form(@StringRes title: Int, get: (AddFormSettings) -> Boolean, set: AddFormSettings.(Boolean) -> AddFormSettings) =
        ToggleSpec(title, null, { get(it.addForm) }, { s, v -> s.copy(addForm = s.addForm.set(v)) })

    /** Optional add-subscription form modules; basic info and dates are always shown. */
    val addFormModules: List<ToggleSpec> = listOf(
        form(R.string.settings_form_popular_button, { it.showPopularButton }, { copy(showPopularButton = it) }),
        form(R.string.settings_form_popular_bundles, { it.showPopularBundles }, { copy(showPopularBundles = it) }),
        form(R.string.settings_form_local_bundle, { it.showLocalBundleOption }, { copy(showLocalBundleOption = it) }),
        form(R.string.settings_form_trial, { it.showTrialOption }, { copy(showTrialOption = it) }),
        form(R.string.settings_form_end_date, { it.showEndDateOptions }, { copy(showEndDateOptions = it) }),
        form(R.string.settings_form_history, { it.showHistoricalPaymentsOption }, { copy(showHistoricalPaymentsOption = it) }),
        form(R.string.settings_form_payment_method, { it.showPaymentMethodSelector }, { copy(showPaymentMethodSelector = it) }),
        form(R.string.settings_form_website, { it.showWebsiteField }, { copy(showWebsiteField = it) }),
        form(R.string.settings_form_notes, { it.showNotesField }, { copy(showNotesField = it) }),
        form(R.string.settings_form_additional_card, { it.showAdditionalOptionsCard }, { copy(showAdditionalOptionsCard = it) }),
        form(R.string.settings_form_shared, { it.showSharedSection }, { copy(showSharedSection = it) }),
        form(R.string.settings_form_tags, { it.showTagManagement }, { copy(showTagManagement = it) }),
    )

    val functionModules: List<ToggleSpec> = listOf(
        ToggleSpec(
            R.string.settings_module_wishlist, R.string.settings_module_wishlist_desc,
            { it.subscriptions.wishlistEnabled },
            { s, v -> s.copy(subscriptions = s.subscriptions.copy(wishlistEnabled = v)) },
        ),
        ToggleSpec(
            R.string.settings_module_fixed_day, R.string.settings_module_fixed_day_desc,
            { it.addForm.showFixedPaymentDay },
            { s, v -> s.copy(addForm = s.addForm.copy(showFixedPaymentDay = v)) },
        ),
        ToggleSpec(
            R.string.settings_module_child_subs, R.string.settings_module_child_subs_desc,
            { it.subscriptions.showChildSubscriptions },
            { s, v -> s.copy(subscriptions = s.subscriptions.copy(showChildSubscriptions = v)) },
        ),
        ToggleSpec(
            R.string.settings_module_archive, R.string.settings_module_archive_desc,
            { it.subscriptions.archiveMode },
            { s, v -> s.copy(subscriptions = s.subscriptions.copy(archiveMode = v)) },
        ),
    )

    val listDisplay: List<ToggleSpec> = listOf(
        ToggleSpec(
            R.string.settings_list_custom_ymd, R.string.settings_list_custom_ymd_desc,
            { it.list.showCustomCycleAsYmd }, { s, v -> s.copy(list = s.list.copy(showCustomCycleAsYmd = v)) },
        ),
        ToggleSpec(
            R.string.settings_list_end_date, R.string.settings_list_end_date_desc,
            { it.list.showEndDateForFixedCycle }, { s, v -> s.copy(list = s.list.copy(showEndDateForFixedCycle = v)) },
        ),
        ToggleSpec(
            R.string.settings_list_iap_total, R.string.settings_list_iap_total_desc,
            { it.list.showIapTotalPrice }, { s, v -> s.copy(list = s.list.copy(showIapTotalPrice = v)) },
        ),
        ToggleSpec(
            R.string.settings_list_lifetime_label, null,
            { it.list.showLifetimeLabel }, { s, v -> s.copy(list = s.list.copy(showLifetimeLabel = v)) },
        ),
        ToggleSpec(
            R.string.settings_list_stored_value_balance, R.string.settings_list_stored_value_balance_desc,
            { it.list.storedValueBalanceMode }, { s, v -> s.copy(list = s.list.copy(storedValueBalanceMode = v)) },
        ),
        ToggleSpec(
            R.string.settings_list_copy_notes, R.string.settings_list_copy_notes_desc,
            { it.list.showCopyNotesButton }, { s, v -> s.copy(list = s.list.copy(showCopyNotesButton = v)) },
        ),
    )
}
