package io.github.submark.core.database

import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: the stored-value kind is gone. Its subscriptions become regular ones, and each deposit stays as
 * spending in the form of an extra purchase, which never moves billing dates. Deductions were not spending.
 */
@DeleteTable(tableName = "stored_value_records")
@DeleteColumn(tableName = "subscriptions", columnName = "storedValueBalance")
@DeleteColumn(tableName = "wallet_transactions", columnName = "storedValueRecordId")
class RemoveStoredValueMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE subscriptions SET kind = 'REGULAR' WHERE kind = 'STORED_VALUE'")
        db.execSQL("UPDATE payment_records SET kind = 'IN_APP_PURCHASE' WHERE kind = 'STORED_VALUE_DEPOSIT'")
    }
}

/**
 * v2 -> v3: the fixed payment day is gone. Its occurrences were "anchor + n cycles, moved to that day", and the
 * stored next payment date already sits on it, so re-anchoring there keeps every later date on the same day.
 * Room's auto migration drops the column before any custom step runs, hence the hand-written table rebuild.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE subscriptions SET cycleAnchorDate = nextPaymentDate " +
                "WHERE fixedPaymentDay IS NOT NULL AND nextPaymentDate IS NOT NULL",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `_new_subscriptions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `bundleRole` TEXT NOT NULL, `parentId` TEXT, `price` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `originalPrice` TEXT, `billingCycle` TEXT, `customCycleCount` INTEGER, `customCycleUnit` TEXT, `isSingleCycle` INTEGER NOT NULL, `renewalType` TEXT NOT NULL, `trialStartDate` INTEGER, `trialDays` INTEGER, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `cycleAnchorDate` INTEGER, `nextPaymentDate` INTEGER, `lastPaymentDate` INTEGER, `status` TEXT NOT NULL, `pausedAt` INTEGER, `categoryId` TEXT NOT NULL, `iconType` TEXT, `iconValue` TEXT, `website` TEXT, `appStoreId` TEXT, `note` TEXT, `paymentMethodId` TEXT, `walletId` TEXT, `isShared` INTEGER NOT NULL, `customReminderEnabled` INTEGER NOT NULL, `calendarSyncEnabled` INTEGER NOT NULL, `calendarEventId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO _new_subscriptions (`id`, `name`, `kind`, `bundleRole`, `parentId`, `price`, `currencyCode`, `originalPrice`, `billingCycle`, `customCycleCount`, `customCycleUnit`, `isSingleCycle`, `renewalType`, `trialStartDate`, `trialDays`, `startDate`, `endDate`, `cycleAnchorDate`, `nextPaymentDate`, `lastPaymentDate`, `status`, `pausedAt`, `categoryId`, `iconType`, `iconValue`, `website`, `appStoreId`, `note`, `paymentMethodId`, `walletId`, `isShared`, `customReminderEnabled`, `calendarSyncEnabled`, `calendarEventId`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `kind`, `bundleRole`, `parentId`, `price`, `currencyCode`, `originalPrice`, `billingCycle`, `customCycleCount`, `customCycleUnit`, `isSingleCycle`, `renewalType`, `trialStartDate`, `trialDays`, `startDate`, `endDate`, `cycleAnchorDate`, `nextPaymentDate`, `lastPaymentDate`, `status`, `pausedAt`, `categoryId`, `iconType`, `iconValue`, `website`, `appStoreId`, `note`, `paymentMethodId`, `walletId`, `isShared`, `customReminderEnabled`, `calendarSyncEnabled`, `calendarEventId`, `createdAt`, `updatedAt` FROM subscriptions")
        db.execSQL("DROP TABLE subscriptions")
        db.execSQL("ALTER TABLE _new_subscriptions RENAME TO subscriptions")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_categoryId` ON `subscriptions` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_parentId` ON `subscriptions` (`parentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_nextPaymentDate` ON `subscriptions` (`nextPaymentDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_kind_status` ON `subscriptions` (`kind`, `status`)")
    }
}
