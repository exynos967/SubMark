package io.github.submark.feature.overview.data

import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.model.PriceMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal read side for the wishlist price-monitor summary on the overview. */
@Singleton
class PriceMonitorReader @Inject constructor(
    private val dao: PriceMonitorDao,
) {
    /** (enabled monitors, any record exists) for the wishlist prices section. */
    fun observeSummary(): Flow<PriceMonitorSummary> = combine(
        dao.observeAll(),
        dao.observeRecordCount(),
    ) { monitors, recordCount ->
        PriceMonitorSummary(
            monitorCount = monitors.count { it.enabled },
            anyPriceRecords = recordCount > 0,
        )
    }
}

data class PriceMonitorSummary(
    val monitorCount: Int,
    val anyPriceRecords: Boolean,
)
