package com.achyut.adglasses.ui.glasses

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.achyut.adglasses.shared.glasses.GlassesSyncFlow
import com.achyut.adglasses.shared.ui.glasses.GlassesSyncFlowPickerDialog
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassesSyncFlowPickerDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun routesTheSelectedFlowWithoutOwningTransferWork() {
        var selected: GlassesSyncFlow? = null

        composeRule.setContent {
            CyanBridgeTheme {
                GlassesSyncFlowPickerDialog(
                    onDismissRequest = {},
                    onFlowSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag("sync_flow_CUSTOM").performClick()

        composeRule.runOnIdle {
            assertEquals(GlassesSyncFlow.CUSTOM, selected)
        }
    }
}
