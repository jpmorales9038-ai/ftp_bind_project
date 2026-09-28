package com.rclonebind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Contenido centrado con ancho máximo, para que en tablets no se estire. */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}
