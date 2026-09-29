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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Espacio inferior que ocupa la barra flotante. Las pantallas lo suman a su
 * relleno final para que el contenido pueda desplazarse por debajo de la
 * píldora (que lo desenfoca) y aun así se llegue al final.
 */
val LocalContentBottomInset = compositionLocalOf { 0.dp }

/**
 * Igual que [LocalContentBottomInset] pero para el borde derecho: en modo
 * apaisado la píldora se muda ahí, así que el contenido reserva ese espacio
 * en vez del inferior.
 */
val LocalContentEndInset = compositionLocalOf { 0.dp }

/**
 * A partir de este ancho de pantalla hay espacio real para dos columnas
 * (apaisado en casi cualquier celular, o una tablet en cualquier
 * orientación); por debajo, una sola columna apilada. Lo usan Servidores
 * (paneles de FTP y Google Drive) e Inicio (montaje y ajustes).
 */
const val DualPaneMinWidthDp = 700

/** true si el ancho actual de pantalla alcanza para un doble panel. */
@Composable
fun rememberIsDualPane(): Boolean = LocalConfiguration.current.screenWidthDp >= DualPaneMinWidthDp

/** Ancho del contenido cuando una pantalla arma doble panel: más que el máximo normal (640–780dp), porque son dos columnas. */
val DualPaneContentWidth = 1080.dp

/**
 * Pantalla con encabezado fijo grande (título + acciones a la derecha) y
 * contenido desplazable debajo. El contenido se corta en seco contra el
 * encabezado.
 *
 * El ancho del contenido se centra y tiene un máximo para que en pantallas
 * angostas (celular en vertical) no cambie nada, pero en pantallas anchas
 * (apaisado, tablets) crezca en vez de dejar franjas vacías a los costados.
 * [maxContentWidth] fuerza un ancho puntual (lo usa Servidores para su doble
 * panel); dejarlo en null usa el cálculo automático.
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
    maxContentWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val headerMaxWidth = adaptiveMaxWidth(screenWidthDp)
    val resolvedMaxWidth = maxContentWidth ?: headerMaxWidth
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = resolvedMaxWidth).fillMaxSize()) {
            // El encabezado siempre usa el ancho angosto de siempre (el mismo
            // que Logs), no el de [maxContentWidth]: ese parámetro solo existe
            // para ensanchar el CUERPO (p. ej. Servidores necesita más ancho
            // para sus dos paneles uno junto al otro en apaisado). Si el
            // encabezado heredara ese ancho más grande, sus acciones (como el
            // botón de agregar) quedarían pegadas al borde real de la
            // pantalla, mucho más cerca de la píldora de navegación de lo que
            // están en cualquier otra pantalla.
            if (title != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = headerMaxWidth).fillMaxWidth()) {
                        ScreenHeader(title, actions)
                    }
                }
            }
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

/**
 * 640dp de siempre para celular en vertical (85% de un ancho típico de
 * ~380–430dp da menos que eso, así que el mínimo de la función gana y no
 * cambia nada). Desde ahí crece con la pantalla hasta 780dp: suficiente
 * para aprovechar el apaisado sin alargar tanto las líneas de texto como
 * para que cueste leerlas.
 */
private fun adaptiveMaxWidth(screenWidthDp: Int): Dp {
    val adaptive = screenWidthDp * 0.85f
    return adaptive.dp.coerceIn(640.dp, 780.dp)
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
