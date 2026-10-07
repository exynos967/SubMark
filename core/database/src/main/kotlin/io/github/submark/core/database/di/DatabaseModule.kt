package io.github.submark.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.submark.core.database.SubMarkDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SubMarkDatabase =
        Room.databaseBuilder(context, SubMarkDatabase::class.java, "submark.db").build()

    @Provides fun subscriptionDao(db: SubMarkDatabase) = db.subscriptionDao()
    @Provides fun categoryDao(db: SubMarkDatabase) = db.categoryDao()
    @Provides fun tagDao(db: SubMarkDatabase) = db.tagDao()
    @Provides fun customFieldDao(db: SubMarkDatabase) = db.customFieldDao()
    @Provides fun subscriptionExtrasDao(db: SubMarkDatabase) = db.subscriptionExtrasDao()
    @Provides fun currencyDao(db: SubMarkDatabase) = db.currencyDao()
    @Provides fun paymentDao(db: SubMarkDatabase) = db.paymentDao()
    @Provides fun paymentMethodDao(db: SubMarkDatabase) = db.paymentMethodDao()
    @Provides fun walletDao(db: SubMarkDatabase) = db.walletDao()
    @Provides fun storedValueDao(db: SubMarkDatabase) = db.storedValueDao()
    @Provides fun sharedDao(db: SubMarkDatabase) = db.sharedDao()
    @Provides fun priceMonitorDao(db: SubMarkDatabase) = db.priceMonitorDao()
    @Provides fun popularRepositoryDao(db: SubMarkDatabase) = db.popularRepositoryDao()
    @Provides fun iconRepositoryDao(db: SubMarkDatabase) = db.iconRepositoryDao()
    @Provides fun apiBudgetDao(db: SubMarkDatabase) = db.apiBudgetDao()
    @Provides fun serviceConnectionDao(db: SubMarkDatabase) = db.serviceConnectionDao()
    @Provides fun backupDao(db: SubMarkDatabase) = db.backupDao()
}
