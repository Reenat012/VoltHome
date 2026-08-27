package ru.mugalimov.volthome.ui.screens.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPriceInputTest {

    @Test
    fun `blank price restores catalog value`() {
        val result = parseCustomPriceInput("")

        assertTrue(result.isValid)
        assertNull(result.kopecks)
    }

    @Test
    fun `rubles with decimal comma are converted without floating point`() {
        val result = parseCustomPriceInput("1234,50")

        assertTrue(result.isValid)
        assertEquals(123_450L, result.kopecks)
    }

    @Test
    fun `malformed or excessive price is rejected`() {
        assertFalse(parseCustomPriceInput("12.345").isValid)
        assertFalse(parseCustomPriceInput("999999999").isValid)
        assertFalse(parseCustomPriceInput("1..2").isValid)
    }
}
