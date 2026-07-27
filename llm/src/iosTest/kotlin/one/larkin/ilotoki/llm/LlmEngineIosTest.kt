package one.larkin.ilotoki.llm

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the real GGUF model through the cinterop path on a simulator.
 *
 * Opt-in, because the model is a 2 GB download the repository cannot carry:
 *
 *     ILOTOKI_TEST_MODEL=/path/to/model.gguf ./gradlew :llm:iosSimulatorArm64Test
 *
 * Without the variable the test reports that it was skipped and passes.
 */
class LlmEngineIosTest {

    @OptIn(ExperimentalForeignApi::class)
    private fun modelPath(): String? = getenv("ILOTOKI_TEST_MODEL")?.toKString()?.takeIf { it.isNotEmpty() }

    @Test
    fun translatesFromTokiPona() = runTest {
        val path = modelPath() ?: run {
            println("skipped: set ILOTOKI_TEST_MODEL to a GGUF file to run this test")
            return@runTest
        }

        val engine = loadLlmEngine(path, LlmParams(temperature = 0.0f, maxTokens = 64))
        try {
            val prompt = "Translate Toki Pona to English.\nQuery: mi olin e sina\nAnswer:"
            val pieces = engine.generate(prompt).toList()
            val text = pieces.joinToString("").trim()

            println("generated: '$text' (${pieces.size} pieces, ${engine.tokensPerSecond} tok/s)")
            assertTrue(pieces.isNotEmpty(), "the engine produced no tokens")
            assertTrue(text.isNotBlank(), "the engine produced only whitespace")
            assertTrue("love" in text.lowercase(), "unexpected translation: '$text'")

            // The same engine has to stay usable for a second, unrelated completion.
            val again = engine.generate(prompt).toList().joinToString("").trim()
            assertTrue(again.isNotBlank(), "the second completion produced nothing")
        } finally {
            engine.close()
        }
    }
}
