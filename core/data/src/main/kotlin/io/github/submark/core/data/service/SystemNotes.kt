package io.github.submark.core.data.service

/**
 * Machine-readable notes written by the data layer into free-text note fields.
 * They start with '@' so the UI can recognise and localize them instead of showing the raw value.
 */
object SystemNotes {
    const val PREFIX = "@"
    const val PAYMENT_DELETED = "@payment_deleted"
    const val PAYMENT_EDITED = "@payment_edited"
    const val SUBSCRIPTION_DELETED = "@subscription_deleted"
    const val STORED_VALUE_RECORD_DELETED = "@stored_value_record_deleted"
    const val INITIAL_BALANCE = "@initial_balance"

    fun isSystem(note: String?): Boolean = note?.startsWith(PREFIX) == true
}
