package com.xs.expensetracker.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptValidationTest {

    private fun validate(
        trackerId: String = "t1",
        sourceId: String = "s1",
        name: String = "Groceries",
        amount: Double = 10.0
    ) = validateReceiptInput(trackerId, sourceId, name, amount)

    @Test
    fun `a well-formed receipt passes`() {
        assertNull(validate())
    }

    @Test
    fun `a blank name is rejected`() {
        assertEquals("Receipt name cannot be empty", validate(name = "   "))
    }

    @Test
    fun `a missing tracker or source is rejected`() {
        assertEquals("Invalid tracker or source", validate(trackerId = ""))
        assertEquals("Invalid tracker or source", validate(sourceId = ""))
    }

    @Test
    fun `zero and negative amounts are rejected`() {
        assertNotNull(validate(amount = 0.0))
        assertNotNull(validate(amount = -5.0))
    }

    @Test
    fun `NaN is rejected before the positivity check`() {
        // NaN fails every comparison, so `amount <= 0` alone would let it through and poison
        // every SUM that later touches the column.
        assertEquals("Amount is not a valid number", validate(amount = Double.NaN))
    }

    @Test
    fun `infinity is rejected`() {
        assertEquals("Amount is not a valid number", validate(amount = Double.POSITIVE_INFINITY))
        assertEquals("Amount is not a valid number", validate(amount = Double.NEGATIVE_INFINITY))
    }
}
