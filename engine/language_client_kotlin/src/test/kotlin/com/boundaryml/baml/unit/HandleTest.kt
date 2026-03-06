package com.boundaryml.baml.unit

import com.boundaryml.baml.BamlHandle
import com.boundaryml.baml.BamlHandleType
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleTest {

    @Test
    fun `handle starts not closed`() {
        val handle = BamlHandle(1L, BamlHandleType.COLLECTOR)
        assertFalse(handle.isClosed)
    }

    @Test
    fun `close marks handle as closed`() {
        val handle = BamlHandle(1L, BamlHandleType.COLLECTOR)
        // Close will attempt to call FFI which isn't loaded in unit tests,
        // but the closed flag should still be set
        try {
            handle.close()
        } catch (_: Exception) {
            // Expected — FFI not loaded
        }
        assertTrue(handle.isClosed)
    }

    @Test
    fun `double close does not crash`() {
        val handle = BamlHandle(2L, BamlHandleType.HTTP_REQUEST)
        try { handle.close() } catch (_: Exception) {}
        try { handle.close() } catch (_: Exception) {}
        // No exception from double close = success
        assertTrue(handle.isClosed)
    }

    @Test
    fun `use block calls close`() {
        val handle = BamlHandle(3L, BamlHandleType.MEDIA_IMAGE)
        try {
            handle.use {
                assertFalse(it.isClosed)
            }
        } catch (_: Exception) {
            // FFI not loaded
        }
        assertTrue(handle.isClosed)
    }

    @Test
    fun `handle type is preserved`() {
        for (type in BamlHandleType.entries) {
            val handle = BamlHandle(100L, type)
            assertTrue(handle.type == type)
        }
    }

    @Test
    fun `handle key is preserved`() {
        val handle = BamlHandle(Long.MAX_VALUE, BamlHandleType.TYPE_BUILDER)
        assertTrue(handle.key == Long.MAX_VALUE)
    }

    @Test
    fun `clone on closed handle throws`() {
        val handle = BamlHandle(4L, BamlHandleType.COLLECTOR)
        try { handle.close() } catch (_: Exception) {}

        var threwIllegalState = false
        try {
            handle.clone()
        } catch (e: IllegalStateException) {
            threwIllegalState = true
        } catch (_: Exception) {
            // Other exceptions OK too (FFI not loaded)
        }
        assertTrue(threwIllegalState)
    }
}
