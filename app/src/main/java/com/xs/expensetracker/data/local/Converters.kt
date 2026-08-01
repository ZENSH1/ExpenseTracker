package com.xs.expensetracker.data.local

import androidx.room.TypeConverter
import com.xs.expensetracker.data.local.entity.ConflictEntityType
import com.xs.expensetracker.domain.data.enums.TransactionType

object Converters {

    // Enums are stored as their `name`, which is what the aggregate queries compare against
    // (e.g. `CASE WHEN r.type = 'INCOME'`). Unknown values decode to null rather than throwing,
    // so a document written by a newer app version can't make the database unreadable.

    @TypeConverter
    fun transactionTypeToString(type: TransactionType?): String? = type?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun conflictTypeToString(type: ConflictEntityType?): String? = type?.name

    @TypeConverter
    fun stringToConflictType(value: String?): ConflictEntityType? =
        value?.let { runCatching { ConflictEntityType.valueOf(it) }.getOrNull() }

    // `sharedWith` is a short list of user ids. Storing it as a delimited string keeps the
    // schema flat; the separator is the ASCII unit separator, which cannot appear in a
    // Firebase uid. Built from its code point rather than written as a literal control
    // character so the source file survives any editor or encoding round-trip.
    private val LIST_SEPARATOR = Char(0x1F).toString()

    @TypeConverter
    fun stringListToString(values: List<String>?): String =
        values.orEmpty().filter { it.isNotBlank() }.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun stringToStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList()
        else value.split(LIST_SEPARATOR).filter { it.isNotBlank() }
}
