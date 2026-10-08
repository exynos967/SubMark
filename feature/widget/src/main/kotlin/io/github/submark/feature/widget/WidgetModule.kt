package io.github.submark.feature.widget

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.data.change.SubscriptionChangeListener
import javax.inject.Inject

/** Updates all widgets after subscription mutations. */
class WidgetChangeListener @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SubscriptionChangeListener {
    override suspend fun onSubscriptionsChanged(ids: Set<String>?) {
        WidgetRefresher.updateAll(context)
    }
}

/** On every launch: refresh widgets and keep the periodic rollover worker scheduled. */
class WidgetAppStartListener @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AppStartListener {
    override suspend fun onAppStart() {
        WidgetRefresher.updateAll(context)
        WidgetRefreshWorker.schedule(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {
    @Provides
    @IntoSet
    fun provideChangeListener(listener: WidgetChangeListener): SubscriptionChangeListener = listener

    @Provides
    @IntoSet
    fun provideAppStartListener(listener: WidgetAppStartListener): AppStartListener = listener
}
