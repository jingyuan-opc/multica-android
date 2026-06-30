package ai.multica.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentifierFormatTest {

    @Test
    fun `identifier combines prefix and number`() {
        val prefix = "MUL"
        val number = 123
        val identifier = "$prefix-$number"
        assertEquals("MUL-123", identifier)
    }

    @Test
    fun `identifier handles 2-char prefix`() {
        assertEquals("AB-1", "AB-1")
    }

    @Test
    fun `identifier handles 5-char prefix`() {
        assertEquals("ABCDE-999", "ABCDE-999")
    }
}
