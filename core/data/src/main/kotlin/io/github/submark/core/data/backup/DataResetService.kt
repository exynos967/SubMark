package io.github.submark.core.data.backup

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.seed.DataSeeder
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.database.SubMarkDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** "Delete all data": every table, secrets and settings (keeping onboarding state and the default currency). */
@Singleton
class DataResetService @Inject constructor(
    private val database: SubMarkDatabase,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val seeder: DataSeeder,
    private val notifier: ChangeNotifier,
) {
    suspend fun clearAll() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        secrets.clear()
        settings.update { old ->
            AppSettings(
                onboardingCompleted = old.onboardingCompleted,
                money = AppSettings().money.copy(defaultCurrencyCode = old.money.defaultCurrencyCode),
            )
        }
        seeder.seed()
        notifier.notifyChanged(null)
    }
}
