package io.github.submark.core.database

import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
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
