package digital.jinatra.tidyra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first build sent instruct models bare text. They did not answer it — they continued
 * it, emitting a turn-end marker and then inventing a whole new conversation. These tests
 * pin the framing and the extraction so that cannot come back.
 */
class PromptTemplateTest {

    @Test
    fun `chatml frames the turn and closes it for the assistant`() {
        val wrapped = PromptTemplate.CHATML.wrap("Rules here", "What are these?")

        assertTrue(wrapped.startsWith("<|im_start|>system"))
        assertTrue(wrapped.contains("<|im_start|>user\nWhat are these?<|im_end|>"))
        assertTrue(wrapped.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `gemma uses its own turn markers`() {
        val wrapped = PromptTemplate.GEMMA.wrap("Rules", "Question")
        assertTrue(wrapped.startsWith("<start_of_turn>user"))
        assertTrue(wrapped.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `an answer is cut at the first stop marker`() {
        val raw = "These are stale installers.<|im_end|>\n<|im_start|>user\nunrelated rambling"
        assertEquals("These are stale installers.", PromptTemplate.CHATML.trim(raw))
    }

    /**
     * The failure that made the answer panel render blank: the model opened with a stop
     * marker, so cutting at the first one left nothing at all.
     */
    @Test
    fun `a leading stop marker does not swallow the whole answer`() {
        val raw = "<|im_end|>\n<|im_start|>assistant\nThey are old APK files.<|im_end|>"
        assertEquals("They are old APK files.", PromptTemplate.CHATML.trim(raw))
    }

    @Test
    fun `a role word at the start of a segment is dropped`() {
        val raw = "<|im_start|>assistant\nTwo duplicate photos.<|im_end|>"
        assertEquals("Two duplicate photos.", PromptTemplate.CHATML.trim(raw))
    }

    @Test
    fun `plain passes text through untouched`() {
        assertEquals("just this", PromptTemplate.PLAIN.trim("  just this  "))
    }
}
