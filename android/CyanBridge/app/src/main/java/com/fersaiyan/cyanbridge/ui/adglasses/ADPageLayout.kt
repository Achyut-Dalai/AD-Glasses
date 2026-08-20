package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact detail-page frame. The top bar owns the page title; content owns any optional hero. */
@Composable
internal fun ADPageLayout(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides ADColors.Ink) {
        Column(Modifier.fillMaxSize()) {
            ADTopBar(title = title, showBack = true, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                content()
            }
        }
    }
}
