package com.fersaiyan.cyanbridge.localmodels.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContextBudgetPolicyTest {
    @Test
    fun qwenTwoKContextCapsOutputAtFiveHundredTwelveTokens() {
        val budget = LocalContextBudgetPolicy.resolve(
            contextTokens = 2048,
            promptTokens = 100,
            requestedOutputTokens = 2048,
        )

        assertEquals(512, budget.effectiveOutputTokens)
        assertTrue(budget.wasOutputClamped)
        assertTrue(
            budget.promptTokens + budget.effectiveOutputTokens + budget.reservedHeadroomTokens <=
                budget.contextTokens,
        )
    }

    @Test
    fun longPromptFurtherReducesAvailableOutput() {
        val budget = LocalContextBudgetPolicy.resolve(
            contextTokens = 2048,
            promptTokens = 1800,
            requestedOutputTokens = 512,
        )

        assertEquals(120, budget.effectiveOutputTokens)
        assertEquals(128, budget.reservedHeadroomTokens)
    }

    @Test
    fun promptWithoutUsefulOutputSpaceIsRejectedClearly() {
        val error = runCatching {
            LocalContextBudgetPolicy.resolve(
                contextTokens = 2048,
                promptTokens = 1900,
                requestedOutputTokens = 512,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("start a new topic"))
    }

    @Test
    fun smallWarmupRequestCanUseSmallRemainingBudget() {
        val budget = LocalContextBudgetPolicy.resolve(
            contextTokens = 2048,
            promptTokens = 1900,
            requestedOutputTokens = 12,
        )

        assertEquals(12, budget.effectiveOutputTokens)
    }
}
