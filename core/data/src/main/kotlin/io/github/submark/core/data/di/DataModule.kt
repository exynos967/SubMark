package io.github.submark.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.data.change.SubscriptionChangeListener
import io.github.submark.core.data.currency.CurrencyCatalog
import io.github.submark.core.data.currency.ExchangeRateSource
import io.github.submark.core.data.currency.FawazSource
import io.github.submark.core.data.currency.FrankfurterSource
import io.github.submark.core.data.secret.KeystoreSecretStore
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.AppSettingsSerializer
import io.github.submark.core.data.settings.DataStoreSettingsRepository
import io.github.submark.core.data.settings.MoneySettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.SystemTimeProvider
import io.github.submark.core.data.time.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds abstract fun timeProvider(impl: SystemTimeProvider): TimeProvider
    @Binds abstract fun secretStore(impl: KeystoreSecretStore): SecretStore
    @Binds abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    /** Empty by default; features add listeners with `@IntoSet`. */
    @Multibinds abstract fun changeListeners(): Set<SubscriptionChangeListener>

    @Multibinds abstract fun appStartListeners(): Set<AppStartListener>
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<AppSettings> {
        val defaults = AppSettings(money = MoneySettings(defaultCurrencyCode = CurrencyCatalog.localeCurrencyCode()))
        return DataStoreFactory.create(
            serializer = AppSettingsSerializer(defaults),
            corruptionHandler = ReplaceFileCorruptionHandler { defaults },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile("app_settings.json") },
        )
    }

    /** Ordered: first source wins, later ones fill gaps and act as fallbacks. */
    @Provides
    @Singleton
    fun rateSources(client: OkHttpClient, json: Json): List<@JvmSuppressWildcards ExchangeRateSource> = listOf(
        FrankfurterSource(client, json),
        FawazSource.jsDelivr(client, json),
        FawazSource.pagesDev(client, json),
    )
}
