package com.example.granitevoice.data.repository.helper

import android.util.Log
import com.example.granitevoice.domain.model.MasterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class StockFuzzyMatchLogic {
    private val TAG = "StockFuzzyMatchLogic"

    companion object {
        const val ITEM_SIMILARITY_THRESHOLD = 0.3f
        const val ITEM_PHONETIC_WEIGHT = 0.3f
        const val ITEM_SEMANTIC_WEIGHT = 0.4f
        const val ITEM_STRUCTURAL_WEIGHT = 0.2f
        const val ITEM_CONTEXTUAL_WEIGHT = 0.1f
    }

    suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>,
        maxResults: Int
    ): Result<List<MasterItem>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting fast AI-based fuzzy matching ===")
        Log.d(TAG, "Query: '$query'")
        Log.d(TAG, "Candidates: ${candidates.size} items")
        val startTime = System.currentTimeMillis()
        try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "\u274c No candidates provided")
                return@withContext Result.success(emptyList())
            }
            val processedQuery = preprocessText(query)
            Log.d(TAG, "Preprocessed query: '$processedQuery'")
            val similarities = mutableListOf<Pair<MasterItem, Float>>()
            val uniqueDescriptions = candidates.map { it.description }.distinct()
            val descriptionScores = mutableMapOf<String, Float>()
            Log.d(TAG, "Processing ${uniqueDescriptions.size} unique descriptions...")
            for (description in uniqueDescriptions) {
                val similarity = calculateAISimilarity(processedQuery, description)
                descriptionScores[description] = similarity
            }
            for (item in candidates) {
                val similarity = descriptionScores[item.description] ?: 0f
                if (similarity > ITEM_SIMILARITY_THRESHOLD) {
                    similarities.add(Pair(item, similarity))
                }
            }
            val topMatches =
                similarities.sortedByDescending { it.second }.take(maxResults).map { it.first }
            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "\u2705 Fast AI fuzzy matching completed in ${totalDuration}ms")
            Log.d(
                TAG,
                "Found ${topMatches.size} matches above threshold $ITEM_SIMILARITY_THRESHOLD"
            )
            topMatches.forEachIndexed { index, item ->
                val similarity = similarities.find { it.first.code == item.code }?.second ?: 0f
                Log.d(
                    TAG,
                    "  ${index + 1}. ${item.code}: ${item.description} (score: ${
                        "%.3f".format(similarity)
                    })"
                )
            }
            Log.d(TAG, "=== Fast AI fuzzy matching completed ===")
            Result.success(topMatches)
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "\u274c Fast AI fuzzy matching failed after ${totalDuration}ms", e)
            Result.failure(e)
        }
    }

    private fun preprocessText(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9x.\\s]"), " ").replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun calculateAISimilarity(query: String, candidate: String): Float {
        val processedCandidate = preprocessText(candidate)
        if (query.equals(processedCandidate, ignoreCase = true)) return 1.0f
        val phoneticSim = phoneticSimilarity(query, processedCandidate)
        val semanticSim = semanticSimilarity(query, processedCandidate)
        val structuralSim = structuralSimilarity(query, processedCandidate)
        val contextSim = contextualSimilarity(query, processedCandidate)
        val totalScore =
            (phoneticSim * ITEM_PHONETIC_WEIGHT) + (semanticSim * ITEM_SEMANTIC_WEIGHT) + (structuralSim * ITEM_STRUCTURAL_WEIGHT) + (contextSim * ITEM_CONTEXTUAL_WEIGHT)
        val boostFactor = calculateBoostFactor(query, processedCandidate)
        val finalScore = minOf(1.0f, totalScore * boostFactor)
        return finalScore
    }

    private fun calculateBoostFactor(query: String, candidate: String): Float {
        val queryWords = query.split(" ").filter { it.length > 1 }
        val candidateWords = candidate.split(" ").filter { it.length > 1 }
        if (queryWords.isEmpty() || candidateWords.isEmpty()) return 1.0f
        val exactMatches = queryWords.count { word1 ->
            candidateWords.any { word2 ->
                word1.equals(
                    word2,
                    ignoreCase = true
                )
            }
        }
        val partialMatches = queryWords.count { queryWord ->
            candidateWords.any { candidateWord ->
                !queryWord.equals(
                    candidateWord,
                    ignoreCase = true
                ) && (areNumbersSimilar(
                    queryWord,
                    candidateWord
                ) || (queryWord.length > 2 && candidateWord.length > 2 && queryWord.take(3)
                    .equals(candidateWord.take(3), ignoreCase = true)))
            }
        }
        val exactRatio = exactMatches.toFloat() / queryWords.size
        val partialRatio = partialMatches.toFloat() / queryWords.size
        val combinedRatio = exactRatio + (partialRatio * 0.7f)
        return when {
            exactRatio >= 0.8f -> 1.4f
            exactRatio >= 0.6f -> 1.3f
            combinedRatio >= 0.8f -> 1.25f
            exactRatio >= 0.4f -> 1.15f
            combinedRatio >= 0.6f -> 1.1f
            else -> 1.0f
        }
    }

    private fun areNumbersSimilar(word1: String, word2: String): Boolean {
        val numbers1 = Regex("\\d+").findAll(word1).map { it.value.toIntOrNull() ?: 0 }.toList()
        val numbers2 = Regex("\\d+").findAll(word2).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (numbers1.isEmpty() || numbers2.isEmpty()) return false
        return numbers1.any { num1 ->
            numbers2.any { num2 ->
                num1 == num2 ||
                        (num1 >= 100 && num2 >= 10 && num1.toString().take(2) == num2.toString()
                            .take(2)) ||
                        (num1.toString().length >= 3 && num2.toString().length >= 3 && num1.toString()
                            .take(3) == num2.toString().take(3)) ||
                        kotlin.math.abs(num1 - num2) <= 10
            }
        }
    }

    private fun phoneticSimilarity(text1: String, text2: String): Float {
        val words1 = text1.split(" ").filter { it.isNotEmpty() }
        val words2 = text2.split(" ").filter { it.isNotEmpty() }
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        var totalSim = 0f
        var matches = 0
        for (word1 in words1) {
            val bestMatch =
                words2.maxOfOrNull { word2 -> phoneticWordSimilarity(word1, word2) } ?: 0f
            totalSim += bestMatch
            matches++
        }
        return if (matches > 0) totalSim / matches else 0f
    }

    private fun phoneticWordSimilarity(word1: String, word2: String): Float {
        val soundex1 = getSoundex(word1)
        val soundex2 = getSoundex(word2)
        if (soundex1 == soundex2) return 1.0f
        val commonChars = soundex1.zip(soundex2).count { it.first == it.second }
        return commonChars.toFloat() / maxOf(soundex1.length, soundex2.length)
    }

    private fun getSoundex(word: String): String {
        if (word.isEmpty()) return "0000"
        val soundexMap = mapOf(
            'b' to '1',
            'f' to '1',
            'p' to '1',
            'v' to '1',
            'c' to '2',
            'g' to '2',
            'j' to '2',
            'k' to '2',
            'q' to '2',
            's' to '2',
            'x' to '2',
            'z' to '2',
            'd' to '3',
            't' to '3',
            'l' to '4',
            'm' to '5',
            'n' to '5',
            'r' to '6'
        )
        val firstChar = word.first().uppercaseChar()
        val digits = word.drop(1).map { soundexMap[it.lowercaseChar()] ?: '0' }.filter { it != '0' }
            .distinct().take(3).joinToString("").padEnd(3, '0')
        return "$firstChar$digits"
    }

    private fun semanticSimilarity(text1: String, text2: String): Float {
        val words1 = text1.split(" ").filter { it.length > 1 }
        val words2 = text2.split(" ").filter { it.length > 1 }
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        val exactMatches =
            words1.count { word1 -> words2.any { word2 -> word1.equals(word2, ignoreCase = true) } }
        val totalWords = words1.size
        val exactMatchRatio = exactMatches.toFloat() / totalWords
        val abbreviationMatches = words1.count { word1 ->
            words2.any { word2 ->
                isAbbreviationMatch(
                    word1,
                    word2
                ) || word1.contains(word2, ignoreCase = true) || word2.contains(
                    word1,
                    ignoreCase = true
                )
            }
        }
        val abbrevMatchRatio = (abbreviationMatches.toFloat() / totalWords) * 0.8f
        var semanticMatch = 0f
        var pairs = 0
        for (word1 in words1) {
            for (word2 in words2) {
                if (!word1.equals(word2, ignoreCase = true)) {
                    semanticMatch += getSemanticWordSimilarity(word1, word2)
                    pairs++
                }
            }
        }
        val avgSemantic = if (pairs > 0) (semanticMatch / pairs) * 0.5f else 0f
        return maxOf(exactMatchRatio, abbrevMatchRatio + avgSemantic)
    }

    private fun isAbbreviationMatch(word1: String, word2: String): Boolean {
        val shorter = if (word1.length < word2.length) word1 else word2
        val longer = if (word1.length >= word2.length) word1 else word2
        return when {
            shorter.length < 2 -> false
            longer.startsWith(shorter, ignoreCase = true) -> true
            shorter.startsWith(longer, ignoreCase = true) -> true
            (shorter.equals("usp", true) && longer.equals("usps", true)) -> true
            (shorter.equals("usps", true) && longer.equals("usp", true)) -> true
            else -> false
        }
    }

    private fun getSemanticWordSimilarity(word1: String, word2: String): Float {
        return when {
            word1.take(4) == word2.take(4) && minOf(word1.length, word2.length) >= 4 -> 0.8f
            word1.take(3) == word2.take(3) && minOf(word1.length, word2.length) >= 3 -> 0.6f
            isSemanticPair(word1, word2) -> 0.7f
            else -> {
                val editSim = editDistanceSimilarity(word1, word2)
                if (editSim > 0.6f) editSim * 0.5f else 0f
            }
        }
    }

    private fun isSemanticPair(word1: String, word2: String): Boolean {
        val pairs = setOf(
            setOf("box", "container"), setOf("screw", "bolt"), setOf("nail", "fastener"),
            setOf("hammer", "tool"), setOf("drill", "tool"), setOf("saw", "tool"),
            setOf("wood", "lumber"), setOf("metal", "steel"), setOf("plastic", "polymer"),
            setOf("red", "crimson"), setOf("blue", "navy"), setOf("green", "emerald"),
            setOf("small", "mini"), setOf("large", "big"), setOf("long", "extended"),
            setOf("pipe", "tube"), setOf("wire", "cable"), setOf("sheet", "panel")
        )
        val wordSet = setOf(word1, word2)
        return pairs.any { it == wordSet }
    }

    private fun structuralSimilarity(text1: String, text2: String): Float {
        val lengthSim = 1f - kotlin.math.abs(text1.length - text2.length).toFloat() / maxOf(
            text1.length,
            text2.length
        )
        val patternSim = characterPatternSimilarity(text1, text2)
        return (lengthSim * 0.3f) + (patternSim * 0.7f)
    }

    private fun characterPatternSimilarity(text1: String, text2: String): Float {
        val chars1 = text1.groupingBy { it }.eachCount()
        val chars2 = text2.groupingBy { it }.eachCount()
        val allChars = (chars1.keys + chars2.keys).toSet()
        var similarity = 0f
        for (char in allChars) {
            val count1 = chars1[char] ?: 0
            val count2 = chars2[char] ?: 0
            if (maxOf(count1, count2) > 0) {
                similarity += minOf(count1, count2).toFloat() / maxOf(count1, count2)
            }
        }
        return similarity / allChars.size
    }

    private fun contextualSimilarity(text1: String, text2: String): Float {
        val numberSim = numberPatternSimilarity(text1, text2)
        val positionSim = positionalSimilarity(text1, text2)
        return (numberSim * 0.6f) + (positionSim * 0.4f)
    }

    private fun numberPatternSimilarity(text1: String, text2: String): Float {
        val pattern1 = Regex("\\d+(?:[x-]\\d+|\\.\\d+)?").findAll(text1).map { it.value }.toSet()
        val pattern2 = Regex("\\d+(?:[x-]\\d+|\\.\\d+)?").findAll(text2).map { it.value }.toSet()
        if (pattern1.isEmpty() && pattern2.isEmpty()) return 1f
        if (pattern1.isEmpty() || pattern2.isEmpty()) return 0f
        val exactMatches = pattern1.intersect(pattern2)
        if (exactMatches.isNotEmpty()) {
            return exactMatches.size.toFloat() / pattern1.union(pattern2).size
        }
        var similarMatches = 0
        for (p1 in pattern1) {
            for (p2 in pattern2) {
                if (areNumberPatternsSimilar(p1, p2)) {
                    similarMatches++
                    break
                }
            }
        }
        return if (similarMatches > 0) {
            (similarMatches.toFloat() / pattern1.union(pattern2).size) * 0.8f
        } else {
            0f
        }
    }

    private fun areNumberPatternsSimilar(pattern1: String, pattern2: String): Boolean {
        val nums1 = Regex("\\d+").findAll(pattern1).map { it.value.toIntOrNull() ?: 0 }.toList()
        val nums2 = Regex("\\d+").findAll(pattern2).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (nums1.isEmpty() || nums2.isEmpty()) return false
        return nums1.any { num1 ->
            nums2.any { num2 ->
                num1 == num2 ||
                        (num1 >= 100 && num2 >= 10 && num1.toString().take(2) == num2.toString()
                            .take(2)) ||
                        (num1.toString().length >= 3 && num2.toString().length >= 3 && num1.toString()
                            .take(3) == num2.toString().take(3)) ||
                        kotlin.math.abs(num1 - num2) <= 5
            }
        }
    }

    private fun positionalSimilarity(text1: String, text2: String): Float {
        val minLength = minOf(text1.length, text2.length)
        if (minLength == 0) return 0f
        var matches = 0
        for (i in 0 until minLength) {
            if (text1[i] == text2[i]) matches++
        }
        return matches.toFloat() / maxOf(text1.length, text2.length)
    }

    private fun editDistanceSimilarity(s1: String, s2: String): Float {
        val distance = calculateLevenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        return if (maxLength == 0) 1f else 1f - (distance.toFloat() / maxLength)
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[s1.length][s2.length]
    }
}
