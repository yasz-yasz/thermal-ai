package com.strukai.thermal

import com.strukai.thermal.network.AiVisionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelHelperTest {

    @Test
    fun extractRecommendedGeminiModel_parses404Message() {
        val msg = "Gemini Error (404): This model models/gemini-2.0-flash is no longer available. Please update your code to use models/gemini-3.8-flash for the latest features."
        assertEquals("gemini-3.8-flash", AiVisionService.extractRecommendedGeminiModel(msg))
    }

    @Test
    fun normalizeGeminiModel_stripsModelsPrefix() {
        assertEquals("gemini-4-hydrogen", AiVisionService.normalizeGeminiModel("models/gemini-4-hydrogen"))
    }

    @Test
    fun rankGeminiVisionModels_prefersGemini4Hydrogen() {
        val ranked = AiVisionService.rankGeminiVisionModels(
            listOf("gemini-2.5-pro", "gemini-3.8-flash", "gemini-4-carbon", "gemini-4-hydrogen")
        )
        assertEquals("gemini-4-hydrogen", ranked.first())
    }

    @Test
    fun buildGeminiRequestPayload_gemini4HydrogenUsesThinkingLevelAndNoTemperature() {
        val payload = AiVisionService.buildGeminiRequestPayload("gemini-4-hydrogen", "AAAA")
        val genConfig = payload.getAsJsonObject("generationConfig")
        assertFalse(genConfig.has("temperature"))
        assertEquals("application/json", genConfig.get("responseMimeType").asString)
        assertEquals("LOW", genConfig.getAsJsonObject("thinkingConfig").get("thinkingLevel").asString)
    }

    @Test
    fun extractGeminiResponseText_skipsThoughtParts() {
        val json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"thought": true, "text": "Internal reasoning..."},
                      {"text": "{\"headerTitle\":\"TEST\"}"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val extracted = AiVisionService.extractGeminiResponseText(json)
        assertTrue(extracted.contains("TEST"))
        assertFalse(extracted.contains("Internal reasoning"))
    }
}
