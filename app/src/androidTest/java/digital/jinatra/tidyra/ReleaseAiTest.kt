package digital.jinatra.tidyra

import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.ai.MediaPipeLlmEngine
import digital.jinatra.tidyra.ai.PromptTemplate
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ReleaseAiTest {
    @Test(timeout = 120000) fun optionalNativeModelLoadsAnswersAndUnloads() = deviceTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.aiFixture") == "true")
        val file = File(context.getExternalFilesDir(null), "release-ai-fixture.task")
        assertEquals("Push the open SmolLM test model before running", 166754726L, file.length())
        val engine = MediaPipeLlmEngine(context)
        try {
            val load = engine.load(file.path, PromptTemplate.CHATML)
            assertTrue("Native model load failed: ${load.exceptionOrNull()}", load.isSuccess)
            assertTrue(engine.isLoaded)
            val answer = engine.complete("The app found two byte-identical copies of a photo. Explain in one short sentence what a duplicate is. Never choose or delete a file.")
            assertTrue("Native model generation failed: ${answer.exceptionOrNull()}", answer.isSuccess)
            assertTrue("The model returned no usable text", answer.getOrThrow().isNotBlank())
        } finally { engine.unload() }
        assertFalse(engine.isLoaded)
        assertTrue(engine.complete("No model is loaded").isFailure)
        file.delete()
        Unit
    }
}
