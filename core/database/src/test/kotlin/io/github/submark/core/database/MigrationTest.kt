package io.github.submark.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SubMarkDatabase::class.java,
        listOf(RemoveStoredValueMigration(), RemovePendingMemberMigration()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun v1ToV2_storedValueBecomesRegularAndDepositsStaySpending() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO categories (id, iconType, iconValue, colorHex, isHidden, sortOrder) VALUES ('c', 'SYMBOL', 'x', '#000000', 0, 0)")
            for ((id, kind) in listOf("sv" to "STORED_VALUE", "reg" to "REGULAR")) {
                execSQL(
                    """INSERT INTO subscriptions (id, name, kind, bundleRole, price, currencyCode, isSingleCycle, renewalType, startDate,
                       status, categoryId, storedValueBalance, isShared, customReminderEnabled, calendarSyncEnabled, createdAt, updatedAt)
                       VALUES ('$id', '$id', '$kind', 'NONE', '10', 'USD', 0, 'AUTO', 0, 'ACTIVE', 'c', '30', 0, 0, 0, 0, 0)""",
                )
            }
            execSQL(
                """INSERT INTO payment_records (id, subscriptionId, amount, currencyCode, paymentDate, status, kind, source, markTiming,
                   dateAdjustmentMode, createdAt, updatedAt)
                   VALUES ('dep', 'sv', '30', 'USD', 0, 'SUCCESS', 'STORED_VALUE_DEPOSIT', 'USER_MANUAL', 'ON_TIME', 'NONE', 0, 0)""",
            )
            execSQL(
                """INSERT INTO stored_value_records (id, subscriptionId, type, amount, currencyCode, amountInSubscriptionCurrency,
                   balanceAfter, isInitial, paymentRecordId, occurredAt, createdAt)
                   VALUES ('r', 'sv', 'DEPOSIT', '30', 'USD', '30', '30', 1, 'dep', 0, 0)""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT id, kind FROM subscriptions ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            assertThat(rows).containsExactly("reg" to "REGULAR", "sv" to "REGULAR").inOrder()
        }
        db.query("SELECT kind, amount FROM payment_records WHERE id = 'dep'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("IN_APP_PURCHASE")
            assertThat(c.getString(1)).isEqualTo("30")
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'stored_value_records'").use { c -> assertThat(c.count).isEqualTo(0) }
    }

    @Test fun v2ToV3_fixedPaymentDayIsFoldedIntoTheAnchor() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO categories (id, iconType, iconValue, colorHex, isHidden, sortOrder) VALUES ('c', 'SYMBOL', 'x', '#000000', 0, 0)")
            // fixed: started on the 15th, paid on the 1st -> next payment 2026-11-01. plain: no fixed day, anchor untouched.
            for ((id, fixedDay, anchor) in listOf(Triple("fixed", "1", START), Triple("plain", "NULL", START))) {
                execSQL(
                    """INSERT INTO subscriptions (id, name, kind, bundleRole, price, currencyCode, billingCycle, isSingleCycle, fixedPaymentDay,
                       renewalType, startDate, cycleAnchorDate, nextPaymentDate, status, categoryId, isShared, customReminderEnabled,
                       calendarSyncEnabled, createdAt, updatedAt)
                       VALUES ('$id', '$id', 'REGULAR', 'NONE', '10', 'USD', 'MONTHLY', 0, $fixedDay, 'AUTO', $START, $anchor, $NEXT,
                       'ACTIVE', 'c', 0, 0, 0, 0, 0)""",
                )
            }
            execSQL(
                """INSERT INTO payment_records (id, subscriptionId, amount, currencyCode, paymentDate, status, kind, source, markTiming,
                   dateAdjustmentMode, createdAt, updatedAt)
                   VALUES ('p', 'fixed', '10', 'USD', $START, 'SUCCESS', 'REGULAR', 'USER_MANUAL', 'ON_TIME', 'NONE', 0, 0)""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

        db.query("SELECT id, cycleAnchorDate, nextPaymentDate FROM subscriptions ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getLong(2))) }
            assertThat(rows).containsExactly(Triple("fixed", NEXT, NEXT), Triple("plain", START, NEXT)).inOrder()
        }
        // Rebuilding the parent table must not cascade into its children.
        db.query("SELECT count(*) FROM payment_records WHERE subscriptionId = 'fixed'").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test fun v3ToV4_invitedMembersBecomeInactive() {
        helper.createDatabase(DB, 3).apply {
            execSQL("INSERT INTO categories (id, iconType, iconValue, colorHex, isHidden, sortOrder) VALUES ('c', 'SYMBOL', 'x', '#000000', 0, 0)")
            execSQL(
                """INSERT INTO subscriptions (id, name, kind, bundleRole, price, currencyCode, billingCycle, isSingleCycle, renewalType,
                   startDate, status, categoryId, isShared, customReminderEnabled, calendarSyncEnabled, createdAt, updatedAt)
                   VALUES ('s', 's', 'REGULAR', 'NONE', '10', 'USD', 'MONTHLY', 0, 'AUTO', $START, 'ACTIVE', 'c', 1, 0, 0, 0, 0)""",
            )
            listOf("a" to "ACTIVE", "i" to "INACTIVE", "p" to "PENDING").forEachIndexed { order, (id, status) ->
                execSQL(
                    """INSERT INTO shared_members (id, subscriptionId, name, status, isCreator, joinedAt, sortOrder, createdAt, updatedAt)
                       VALUES ('$id', 's', '$id', '$status', 0, $START, $order, 0, 0)""",
                )
            }
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 4, true)

        db.query("SELECT id, status FROM shared_members ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            assertThat(rows).containsExactly("a" to "ACTIVE", "i" to "INACTIVE", "p" to "INACTIVE").inOrder()
        }
    }

    private companion object {
        const val DB = "migration-test"
        val START = java.time.LocalDate.of(2026, 9, 15).toEpochDay()
        val NEXT = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
    }
}
