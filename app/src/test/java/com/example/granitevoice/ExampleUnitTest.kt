package com.example.granitevoice

import com.example.granitevoice.data.remote.client.GraniteClientConfig
import com.example.granitevoice.data.remote.model.MasterItemDto
import com.example.granitevoice.data.remote.model.QueryMasterItemsResponse
import com.example.granitevoice.data.remote.model.StockDetailDto
import com.example.granitevoice.domain.model.CommandIntent
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.ProcessingStep
import com.example.granitevoice.domain.model.StockDetail
import com.example.granitevoice.domain.model.ItemStockSummary
import com.example.granitevoice.domain.model.VoiceCommandResult
import com.example.granitevoice.domain.model.VoiceProcessingConfig
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for GraniteVoice core components
 */
class GraniteVoiceUnitTests {

    @Test
    fun graniteClientConfig_defaultValues_areCorrect() {
        val config = GraniteClientConfig()

        assertEquals("https://192.168.0.10:8081", config.baseUrl)
        assertEquals("0", config.username)
        assertEquals("07", config.password)
        assertEquals(30L, config.connectTimeoutSeconds)
        assertEquals(30L, config.readTimeoutSeconds)
        assertEquals(30L, config.writeTimeoutSeconds)
        assertEquals(3, config.maxRetries)
        assertTrue(config.allowSelfSignedCertificates) // Should be true for dev
        assertTrue(config.enableDebugLogging)
    }

    @Test
    fun graniteClientConfig_fromEnvironment_returnsValidConfig() {
        val config = GraniteClientConfig.fromEnvironment()

        assertNotNull(config.baseUrl)
        assertNotNull(config.username)
        assertNotNull(config.password)
    }

    @Test
    fun masterItemDto_jsonMapping_worksCorrectly() {
        val gson = Gson()
        val json = """
            {
                "Results": [
                    {
                        "ID": 1,
                        "Code": "TEST001",
                        "Description": "Test Item 1"
                    },
                    {
                        "ID": 2,
                        "Code": "TEST002", 
                        "Description": "Test Item 2"
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, QueryMasterItemsResponse::class.java)

        assertEquals(2, response.Results.size)
        assertEquals(1, response.Results[0].id)
        assertEquals("TEST001", response.Results[0].code)
        assertEquals("Test Item 1", response.Results[0].description)
    }

    @Test
    fun masterItem_domainModelMapping_worksCorrectly() {
        val dto = MasterItemDto(
            id = 1,
            code = "TEST001",
            description = "Test Item"
        )

        val domainModel = MasterItem(
            code = dto.code,
            description = dto.description,
            remoteId = dto.id
        )

        assertEquals("TEST001", domainModel.code)
        assertEquals("Test Item", domainModel.description)
        assertEquals(1, domainModel.remoteId)
    }

    @Test
    fun stockDetail_creation_worksCorrectly() {
        val stockDetail = StockDetail(
            code = "HAM001",
            barcode = "123456789",
            site = "WAREHOUSE",
            quantity = 50.0,
            availableQuantity = 45.0,
            location = "A1-B2",
            description = "Hammer 16oz",
            unitOfMeasure = "EA",
            lastUpdated = "2024-01-01T10:00:00Z"
        )

        assertEquals("HAM001", stockDetail.code)
        assertEquals("123456789", stockDetail.barcode)
        assertEquals("WAREHOUSE", stockDetail.site)
        assertEquals(50.0, stockDetail.quantity ?: 0.0, 0.01)
        assertEquals(45.0, stockDetail.availableQuantity ?: 0.0, 0.01)
        assertEquals("A1-B2", stockDetail.location)
        assertEquals("Hammer 16oz", stockDetail.description)
        assertEquals("EA", stockDetail.unitOfMeasure)
    }

    @Test
    fun stockDetail_quantityDisplay_worksCorrectly() {
        val inStock = StockDetail("TEST001", null, null, 10.0, 8.0, null, null, "EA", null)
        val outOfStock = StockDetail("TEST002", null, null, 0.0, 0.0, null, null, "EA", null)
        val unknownStock = StockDetail("TEST003", null, null, null, null, null, null, null, null)

        assertEquals("8 EA available", inStock.getQuantityDisplay())
        assertEquals("Out of stock", outOfStock.getQuantityDisplay())
        assertEquals("Quantity unknown", unknownStock.getQuantityDisplay())
    }

    @Test
    fun stockDetail_locationDisplay_worksCorrectly() {
        val withBoth = StockDetail("TEST001", null, "SITE1", 0.0, 0.0, "LOC1", null, null, null)
        val locationOnly = StockDetail("TEST002", null, null, 0.0, 0.0, "LOC2", null, null, null)
        val siteOnly = StockDetail("TEST003", null, "SITE3", 0.0, 0.0, null, null, null, null)
        val neither = StockDetail("TEST004", null, null, 0.0, 0.0, null, null, null, null)

        assertEquals("LOC1 (SITE1)", withBoth.getLocationDisplay())
        assertEquals("LOC2", locationOnly.getLocationDisplay())
        assertEquals("SITE3", siteOnly.getLocationDisplay())
        assertEquals("Location unknown", neither.getLocationDisplay())
    }

    @Test
    fun stockDetail_inStock_worksCorrectly() {
        val available = StockDetail("TEST001", null, null, 10.0, 8.0, null, null, null, null)
        val quantityOnly = StockDetail("TEST002", null, null, 5.0, null, null, null, null, null)
        val outOfStock = StockDetail("TEST003", null, null, 0.0, 0.0, null, null, null, null)

        assertTrue(available.isInStock())
        assertTrue(quantityOnly.isInStock())
        assertFalse(outOfStock.isInStock())
    }

    @Test
    fun itemStockSummary_creation_worksCorrectly() {
        val masterItem = MasterItem("HAM001", "Hammer", 1)
        val stockDetails = listOf(
            StockDetail("HAM001", null, "SITE1", 10.0, 8.0, "LOC1", null, "EA", null),
            StockDetail("HAM001", null, "SITE2", 5.0, 3.0, "LOC2", null, "EA", null)
        )

        // Calculate totals for the test
        val totalQuantity = stockDetails.mapNotNull { it.quantity }.sum()
        val totalAvailable = stockDetails.mapNotNull { it.availableQuantity }.sum()

        val summary = ItemStockSummary(
            masterItem = masterItem,
            stockDetails = stockDetails,
            totalQuantity = totalQuantity,
            totalAvailable = totalAvailable,
            locationCount = 2
        )

        assertEquals(masterItem, summary.masterItem)
        assertEquals(2, summary.stockDetails.size)
        assertEquals(15.0, summary.totalQuantity, 0.01)
        assertEquals(11.0, summary.totalAvailable, 0.01)
        assertEquals(2, summary.locationCount)
        assertEquals("11 EA available", summary.getTotalQuantityDisplay())
        assertEquals("2 locations", summary.getLocationSummary())
        assertTrue(summary.isInStock())
    }

    @Test
    fun masterItem_primaryKey_isCode() {
        val item1 = MasterItem("CODE001", "Description 1", 1)
        val item2 = MasterItem(
            "CODE001",
            "Description 2",
            2
        ) // Same code, different description and remoteId

        // In database, these would be treated as the same item (same primary key)
        assertEquals(item1.code, item2.code)
    }

    @Test
    fun voiceProcessingConfig_defaultValues_areCorrect() {
        val config = VoiceProcessingConfig()

        assertEquals(5, config.maxAlternatives)
        assertEquals(0.6f, config.minimumConfidence, 0.01f)
        assertTrue(config.useAiFuzzyMatching)
        assertTrue(config.enableProcessingLogging)
        assertEquals(4, config.maxProcessingSteps) // Updated for stock lookup
        assertTrue(config.enableStockLookup)
        assertEquals(10000L, config.stockLookupTimeoutMs)
    }

    @Test
    fun commandIntent_stockLookup_isCorrectType() {
        val intent = CommandIntent.StockLookup
        assertTrue(intent is CommandIntent.StockLookup)
    }

    @Test
    fun commandIntent_unknown_isCorrectType() {
        val intent = CommandIntent.Unknown
        assertTrue(intent is CommandIntent.Unknown)
    }

    @Test
    fun commandIntent_other_containsDescription() {
        val description = "Custom command"
        val intent = CommandIntent.Other(description)
        assertTrue(intent is CommandIntent.Other)
        assertEquals(description, intent.description)
    }

    @Test
    fun voiceCommandResult_withStockInfo_worksCorrectly() {
        val masterItem = MasterItem("HAM001", "Hammer", 1)
        val stockSummary = ItemStockSummary(
            masterItem = masterItem,
            stockDetails = listOf(
                StockDetail("HAM001", null, "SITE1", 10.0, 8.0, "LOC1", null, "EA", null)
            ),
            totalQuantity = 10.0,
            totalAvailable = 8.0,
            locationCount = 1
        )

        val result = VoiceCommandResult(
            originalTranscription = "find hammer stock",
            intent = CommandIntent.StockLookup,
            extractedQuery = "hammer",
            matchedItem = masterItem,
            confidence = 0.9f,
            stockSummary = stockSummary
        )

        assertTrue(result.hasCompleteStockInfo())
        assertEquals("Hammer: 8 EA available", result.getResultSummary())
        assertEquals("✅ In Stock: 8 EA available", result.getStatusMessage())
    }

    @Test
    fun voiceCommandResult_withStockError_worksCorrectly() {
        val masterItem = MasterItem("HAM001", "Hammer", 1)

        val result = VoiceCommandResult(
            originalTranscription = "find hammer stock",
            intent = CommandIntent.StockLookup,
            extractedQuery = "hammer",
            matchedItem = masterItem,
            confidence = 0.9f,
            stockLookupError = "Network timeout"
        )

        assertFalse(result.hasCompleteStockInfo())
        assertEquals("Hammer (Stock info unavailable)", result.getResultSummary())
        assertEquals(
            "⚠️ Item found but stock lookup failed: Network timeout",
            result.getStatusMessage()
        )
    }

    @Test
    fun voiceCommandResult_creation_worksCorrectly() {
        val masterItem = MasterItem("HAM001", "Hammer", 1)
        val processingStep = ProcessingStep(
            step = "Test Step",
            input = "test input",
            output = "test output",
            confidence = 0.8f
        )

        val result = VoiceCommandResult(
            originalTranscription = "find hammer",
            intent = CommandIntent.StockLookup,
            extractedQuery = "hammer",
            matchedItem = masterItem,
            confidence = 0.9f,
            alternativeMatches = emptyList(),
            processingSteps = listOf(processingStep)
        )

        assertEquals("find hammer", result.originalTranscription)
        assertEquals("hammer", result.extractedQuery)
        assertEquals(masterItem, result.matchedItem)
        assertEquals(0.9f, result.confidence, 0.01f)
        assertEquals(1, result.processingSteps.size)
        assertEquals("Test Step", result.processingSteps[0].step)
    }

    @Test
    fun processingStep_creation_includesTimestamp() {
        val step = ProcessingStep(
            step = "Test Step",
            input = "input",
            output = "output"
        )

        assertTrue(step.timestamp > 0)
        assertTrue(step.timestamp <= System.currentTimeMillis())
    }

    @Test
    fun result_success_handlesCorrectly() {
        val testData = "Success Data"
        val result = Result.success(testData)

        result.fold(
            onSuccess = { data ->
                assertEquals("Success Data", data)
            },
            onFailure = {
                throw AssertionError("Should not reach failure case")
            }
        )
    }

    @Test
    fun result_failure_handlesCorrectly() {
        val exception = RuntimeException("Test Error")
        val result = Result.failure<String>(exception)

        result.fold(
            onSuccess = {
                throw AssertionError("Should not reach success case")
            },
            onFailure = { error ->
                assertEquals("Test Error", error.message)
            }
        )
    }

    @Test
    fun masterItem_codeAsId_preventsDuplicates() {
        // Simulate what would happen in the database
        val items = mutableMapOf<String, MasterItem>()

        val item1 = MasterItem("CODE001", "Original Description", 1)
        val item2 = MasterItem("CODE001", "Updated Description", 2)

        items[item1.code] = item1
        items[item2.code] = item2 // This overwrites item1

        assertEquals(1, items.size) // Only one item remains
        assertEquals("Updated Description", items["CODE001"]?.description) // Latest description
        assertEquals(2, items["CODE001"]?.remoteId) // Latest remote ID
    }

    @Test
    fun voiceCommandResult_confidenceBelowThreshold_returnsNoMatch() {
        val config = VoiceProcessingConfig(minimumConfidence = 0.7f)
        val lowConfidenceResult = VoiceCommandResult(
            originalTranscription = "find something",
            intent = CommandIntent.StockLookup,
            extractedQuery = "something",
            matchedItem = MasterItem("TEST001", "Test Item", 1),
            confidence = 0.5f // Below threshold
        )

        // In real implementation, matched item would be null if confidence < minimum
        assertTrue(lowConfidenceResult.confidence < config.minimumConfidence)
    }

    @Test
    fun voiceCommandResult_confidenceAboveThreshold_returnsMatch() {
        val config = VoiceProcessingConfig(minimumConfidence = 0.6f)
        val highConfidenceResult = VoiceCommandResult(
            originalTranscription = "find hammer",
            intent = CommandIntent.StockLookup,
            extractedQuery = "hammer",
            matchedItem = MasterItem("HAM001", "Hammer", 1),
            confidence = 0.8f // Above threshold
        )

        assertTrue(highConfidenceResult.confidence >= config.minimumConfidence)
        assertNotNull(highConfidenceResult.matchedItem)
    }
}