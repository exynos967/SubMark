package io.github.submark.feature.integrations.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.feature.integrations.data.price.PriceCheckWorker
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Reschedules the periodic price check at every app launch from current settings. */
@Singleton
class PriceMonitorScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : AppStartListener {
    override suspend fun onAppStart() {
        val integration = settings.settings.first().integrations
        PriceCheckWorker.schedule(context, integration.priceMonitorEnabled, integration.priceMonitorIntervalHours)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationsModule {
    @Binds
    @IntoSet
    abstract fun priceMonitorScheduler(listener: PriceMonitorScheduler): AppStartListener
}
