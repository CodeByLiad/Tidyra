package digital.jinatra.tidyra.core.cache

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CacheBatchTest {
    @Test fun actsOnlyOnDistinctApprovedAppsAndRetainsPartialFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val results = CacheBatch.run(listOf("chosen.a", "chosen.b", "chosen.a"), { true }, {
            calls += it
            CacheBatch.Item(it, if (it == "chosen.a") CacheBatch.Status.CONFIRMED else CacheBatch.Status.FAILED, "result")
        }, {})
        assertEquals(listOf("chosen.a", "chosen.b"), calls)
        assertEquals(listOf(CacheBatch.Status.CONFIRMED, CacheBatch.Status.FAILED), results.map { it.status })
    }
    @Test fun capabilityLossSkipsRestInsteadOfRepeatingTimeout() = runBlocking {
        var available = true
        var calls = 0
        val results = CacheBatch.run(listOf("a", "b", "c"), { available }, {
            calls++; available = false
            CacheBatch.Item(it, CacheBatch.Status.FAILED, "timeout")
        }, {})
        assertEquals(1, calls)
        assertEquals(2, results.count { it.status == CacheBatch.Status.NOT_ATTEMPTED })
    }
    @Test fun unavailableBackendSubmitsNoRequests() = runBlocking {
        val results = CacheBatch.run(listOf("a", "b"), { false }, { error("must not run") }, {})
        assertTrue(results.all { it.status == CacheBatch.Status.NOT_ATTEMPTED })
    }
    @Test fun cancellationDoesNotSubmitNextApp() = runBlocking {
        val calls = mutableListOf<String>()
        val job = launch {
            CacheBatch.run(listOf("a", "b"), { true }, {
                calls += it
                currentCoroutineContext().cancel()
                CacheBatch.Item(it, CacheBatch.Status.CONFIRMED, "confirmed")
            }, {})
        }
        job.join()
        assertEquals(listOf("a"), calls)
        assertTrue(job.isCancelled)
    }
    @Test fun cachePolicyAllowsOrdinarySystemAppsButProtectsCoreAndSharedIdentity() {
        assertNull(CachePolicy.blockedReason("com.android.chrome", 10123, false, false))
        assertNotNull(CachePolicy.blockedReason("android", 1000, false, false))
        assertNotNull(CachePolicy.blockedReason("core", 10123, true, false))
        assertNotNull(CachePolicy.blockedReason("shared", 10123, false, true))
        assertNotNull(CachePolicy.blockedReason("digital.jinatra.tidyra.usbtest", 10123, false, false))
        assertNotNull(CachePolicy.blockedReason("moe.shizuku.privileged.api", 10123, false, false))
        assertNotNull(CachePolicy.blockedReason("profile.core", 101000, false, false))
    }
}
