package ai.multica.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiResultTest {

    @Test
    fun `map transforms success data`() {
        val r: ApiResult<Int> = ApiResult.Success(42)
        val mapped = r.map { it * 2 }
        assertEquals(ApiResult.Success(84), mapped)
    }

    @Test
    fun `map leaves errors untouched`() {
        val r: ApiResult<Int> = ApiResult.HttpError(500, "boom")
        val mapped = r.map { it * 2 }
        assertEquals(r, mapped)
    }

    @Test
    fun `getOrNull returns data on success`() {
        assertEquals(42, ApiResult.Success(42).getOrNull())
    }

    @Test
    fun `getOrNull returns null on error`() {
        assertEquals(null, ApiResult.HttpError(404, "").getOrNull())
    }

    @Test
    fun `onSuccess fires block only on success`() {
        var count = 0
        ApiResult.Success(1).onSuccess { v -> count += v }
        assertEquals(1, count)
        ApiResult.HttpError(500, "x").onSuccess { _: Int -> count += 0 }
        assertEquals(1, count)
    }
}
