package io.github.submark.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.submark.core.database.dao.ApiBudgetDao
import io.github.submark.core.database.dao.BackupDao
import io.github.submark.core.database.dao.CategoryDao
import io.github.submark.core.database.dao.CurrencyDao
import io.github.submark.core.database.dao.CustomFieldDao
import io.github.submark.core.database.dao.IconRepositoryDao
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.PaymentMethodDao
import io.github.submark.core.database.dao.PopularRepositoryDao
import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.database.dao.ServiceConnectionDao
import io.github.submark.core.database.dao.SharedDao
import io.github.submark.core.database.dao.StoredValueDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.SubscriptionExtrasDao
import io.github.submark.core.database.dao.TagDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.model.ApiBudgetConfig
import io.github.submark.core.model.BackupJob
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.model.Category
import io.github.submark.core.model.Currency
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.CustomFieldValue
import io.github.submark.core.model.CustomReminder
import io.github.submark.core.model.HistoricalRate
import io.github.submark.core.model.IconRepository
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PopularRepository
import io.github.submark.core.model.PriceMonitor
import io.github.submark.core.model.PriceRecord
import io.github.submark.core.model.ServiceConnection
import io.github.submark.core.model.SharedConfig
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.StoredValueRecord
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionPhoto
import io.github.submark.core.model.SubscriptionTag
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagFolderTag
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletTransaction

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        Subscription::class, Category::class, Tag::class, SubscriptionTag::class, TagFolder::class, TagFolderTag::class,
        CustomFieldDefinition::class, CustomFieldOption::class, CustomFieldValue::class, SubscriptionPhoto::class,
        CustomReminder::class, Currency::class, HistoricalRate::class, PaymentRecord::class, PaymentMethod::class,
        Wallet::class, WalletTransaction::class, StoredValueRecord::class, SharedConfig::class, SharedMember::class,
        PriceMonitor::class, PriceRecord::class, PopularRepository::class, IconRepository::class,
        ApiBudgetConfig::class, ServiceConnection::class, BackupProfile::class, BackupJob::class,
    ],
)
@TypeConverters(Converters::class)
abstract class SubMarkDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun subscriptionExtrasDao(): SubscriptionExtrasDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun paymentDao(): PaymentDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun walletDao(): WalletDao
    abstract fun storedValueDao(): StoredValueDao
    abstract fun sharedDao(): SharedDao
    abstract fun priceMonitorDao(): PriceMonitorDao
    abstract fun popularRepositoryDao(): PopularRepositoryDao
    abstract fun iconRepositoryDao(): IconRepositoryDao
    abstract fun apiBudgetDao(): ApiBudgetDao
    abstract fun serviceConnectionDao(): ServiceConnectionDao
    abstract fun backupDao(): BackupDao
}
