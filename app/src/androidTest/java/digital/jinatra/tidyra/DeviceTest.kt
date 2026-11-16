package digital.jinatra.tidyra

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Invoke the target's suspend API without requiring test-only coroutine facade APIs
 * to be retained inside the optimized production APK. */
internal fun <T> deviceTest(block: suspend () -> T): T {
    val finished = CountDownLatch(1)
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result; finished.countDown() }
    })
    check(finished.await(120, TimeUnit.SECONDS)) { "Device operation did not complete within the test deadline" }
    return outcome!!.getOrThrow()
}
