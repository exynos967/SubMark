package io.github.submark.feature.integrations.panel

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.feature.integrations.panel.worker.PanelRefreshWorker
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules the periodic panel refresh worker at every app launch. */
@Singleton
class PanelAppStartListener @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppStartListener {
    override suspend fun onAppStart() {
        PanelRefreshWorker.schedule(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PanelModule {
    @Binds
    @IntoSet
    abstract fun bindAppStartListener(listener: PanelAppStartListener): AppStartListener
}
