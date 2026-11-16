package digital.jinatra.tidyra.ai

import digital.jinatra.tidyra.core.model.Bytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ModelDownloaderTest {
    @get:Rule val temp = TemporaryFolder()
    private val target get() = File(temp.root, "model.task")
    private val partial get() = File(target.path + ".part")
    private fun model(url: String) = ModelCatalog.Model("test", "Test", "test", Bytes(8), 2, false,
        url, "model.task", "Test", needsHuggingFaceToken = false)

    @Test fun `server ignoring range replaces partial and reports the correct total`() = runBlocking {
        MockWebServer().use { server ->
            partial.writeText("old")
            server.enqueue(MockResponse().setBody("abcdefgh"))
            val events = withTimeout(5000) { ModelDownloader({ target }).download(model(server.url("/model").toString()), null).toList() }
            assertTrue(events.last() is ModelDownloader.Progress.Done)
            assertEquals("abcdefgh", target.readText())
            assertEquals(8L, (events.first { it is ModelDownloader.Progress.Downloading } as ModelDownloader.Progress.Downloading).total.value)
        }
    }

    @Test fun `valid range resumes exactly after saved bytes`() = runBlocking {
        MockWebServer().use { server ->
            partial.writeText("abc")
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-7/8").setBody("defgh"))
            val events = withTimeout(5000) { ModelDownloader({ target }).download(model(server.url("/model").toString()), null).toList() }
            assertTrue(events.last() is ModelDownloader.Progress.Done)
            assertEquals("abcdefgh", target.readText())
            assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun `different response range preserves the existing partial file`() = runBlocking {
        MockWebServer().use { server ->
            partial.writeText("abc")
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-4/8").setBody("abcde"))
            val events = withTimeout(5000) { ModelDownloader({ target }).download(model(server.url("/model").toString()), null).toList() }
            assertTrue(events.last() is ModelDownloader.Progress.Failed)
            assertEquals("abc", partial.readText())
            assertFalse(target.exists())
        }
    }

    @Test fun `cancel stops a slow response without promoting an incomplete model`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("a".repeat(10240)).throttleBody(1024, 1, TimeUnit.SECONDS))
            withTimeout(5000) { ModelDownloader({ target }).download(model(server.url("/model").toString()), null).first() }
            delay(200)
            val stoppedAt = partial.length()
            delay(1200)
            assertEquals(stoppedAt, partial.length())
            assertTrue(stoppedAt in 1 until 10240)
            assertFalse(target.exists())
        }
    }

    @Test fun `truncated body remains resumable and is never installed`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abc").setHeader("Content-Length", 100).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
            val events = withTimeout(5000) { ModelDownloader({ target }).download(model(server.url("/model").toString()), null).toList() }
            assertTrue(events.last() is ModelDownloader.Progress.Failed)
            assertFalse(target.exists())
            assertTrue(partial.isFile)
        }
    }
}
