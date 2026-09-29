package com.rclonebind.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Espacio inferior que ocupa la barra flotante. Las pantallas lo suman a su
 * relleno final para que el contenido pueda desplazarse por debajo de la
 * píldora (que lo desenfoca) y aun así se llegue al final.
 */
val LocalContentBottomInset = compositionLocalOf { 0.dp }

/**
 * Espacio a la derecha que ocupa la píldora en modo apaisado (ahí es vertical
 * y va pegada a ese borde). Mismo propósito que [LocalContentBottomInset]
 * pero para cuando la barra se traslada a un lado en vez de abajo.
 */
val LocalContentEndInset = compositionLocalOf { 0.dp }

/**
 * Pantalla con encabezado fijo grande (título + acciones a la derecha) y
 * contenido desplazable debajo. El contenido se corta en seco contra el
 * encabezado. Ancho máximo para que en tablets no se estire.
 *
 * Con [onRefresh] el contenido admite "deslizar hacia abajo para actualizar";
 * [refreshing] indica si hay una actualización en curso (muestra el indicador).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            if (title != null) ScreenHeader(title, actions)
            if (onRefresh != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    ScreenBody(Modifier, scroll, scrollState, content)
                }
            } else {
                ScreenBody(Modifier.weight(1f).fillMaxWidth(), scroll, scrollState, content)
            }
        }
    }
}

@Composable
private fun ScreenBody(
    modifier: Modifier,
    scroll: Boolean,
    scrollState: ScrollState,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (scroll) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp + LocalContentEndInset.current,
                bottom = 16.dp + LocalContentBottomInset.current
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
