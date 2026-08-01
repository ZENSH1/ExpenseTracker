package com.xs.expensetracker.data

import com.xs.expensetracker.data.local.Converters
import com.xs.expensetracker.domain.data.enums.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    @Test
    fun `a shared-with list survives a round trip`() {
        val ids = listOf("uid-alice", "uid-bob", "local:device-1")
        assertEquals(ids, Converters.stringToStringList(Converters.stringListToString(ids)))
    }

    @Test
    fun `an empty list round trips to empty, not to a blank entry`() {
        assertTrue(Converters.stringToStringList(Converters.stringListToString(emptyList())).isEmpty())
        assertTrue(Converters.stringToStringList(null).isEmpty())
        assertTrue(Converters.stringToStringList("").isEmpty())
    }

    @Test
    fun `a single id round trips`() {
        assertEquals(listOf("uid-alice"), Converters.stringToStringList(Converters.stringListToString(listOf("uid-alice"))))
    }

    @Test
    fun `blank ids are dropped rather than stored as empty entries`() {
        assertEquals(
            listOf("uid-alice"),
            Converters.stringToStringList(Converters.stringListToString(listOf("uid-alice", "", "  ")))
        )
    }

    @Test
    fun `transaction types round trip by name`() {
        TransactionType.entries.forEach { type ->
            assertEquals(type, Converters.stringToTransactionType(Converters.transactionTypeToString(type)))
        }
    }

    @Test
    fun `an unknown type decodes to null instead of throwing`() {
        // A document written by a newer app version must not make the database unreadable.
        assertNull(Converters.stringToTransactionType("SOMETHING_NEW"))
        assertNull(Converters.stringToTransactionType(null))
    }
}
