package com.auto.odo

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testLocaleFormattingFix() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            
            // Using US locale formatting guarantees a dot decimal separator
            val formatted = String.format(Locale.US, "%.2f", 10.5)
            assertEquals("10.50", formatted)
            
            // Parsing works correctly with dot decimal separator
            val parsed = formatted.toDoubleOrNull()
            assertEquals(10.5, parsed)
            
            // Parsing values entered with a comma (e.g. from German/French inputs) also works via replace(',', '.')
            val userCommaInput = "10,50"
            val parsedComma = userCommaInput.replace(',', '.').toDoubleOrNull()
            assertEquals(10.5, parsedComma)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

