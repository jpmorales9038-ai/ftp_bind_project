package com.rclonebind.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rclonebind.app.ui.theme.AppMotion
import kotlinx.coroutines.isActive

/**
 * Espacio inferior que ocupa la barra flotante. Las pantallas lo suman a su
 * relleno final para que el contenido pueda desplazarse por debajo de la
 * píldora (que lo desenfoca) y aun así se llegue al final.
 */
val LocalContentBottomInset = compositionLocalOf { 0.dp }

/**
 * Espacio a la derecha que ocupa la píldora cuando se mueve a ese costado
 * (apaisado). En retrato vale 0 (ahí el espacio lo reserva el inset
 * inferior de arriba). Lo aplica [ScreenContainer] en las 4 pantallas por
 * igual, así que ninguna pantalla necesita saber de la píldora por su
 * cuenta: antes, Inicio y Servidores (las que usan más ancho en apaisado
 * con su doble panel) quedaban con contenido tapado detrás de la píldora.
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
 * Alto del difuminado bajo el encabezado. Es el espejo del que hay sobre la
 * barra de gestos (MainActivity), pero más corto: el encabezado ya ocupa su
 * propio espacio y el de abajo es más alto porque además cubre la píldora.
 * En apaisado se reduce igual que el inferior, porque la pantalla tiene
 * mucha menos altura.
 */
private val TopFadeHeight = 32.dp
private val TopFadeHeightLandscape = 20.dp

/**
 * Pantalla con encabezado fijo grande (título + acciones a la derecha) y
 * contenido desplazable debajo. Al desplazarse, el contenido se funde con el
 * fondo contra el encabezado con el mismo degradado que la barra de gestos
 * inferior (ver [TopFade]).
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
    val resolvedMaxWidth = maxContentWidth ?: adaptiveMaxWidth(screenWidthDp)
    val pullState = if (onRefresh != null) rememberPullToRefreshState() else null
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = resolvedMaxWidth)
                .fillMaxSize()
                .let { base ->
                    if (pullState != null && onRefresh != null) {
                        base.pullToRefresh(isRefreshing = refreshing, state = pullState, onRefresh = onRefresh)
                    } else base
                }
        ) {
            if (title != null) {
                ScreenHeader(title, actions, LocalContentEndInset.current, pullState, refreshing)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScreenBody(Modifier.fillMaxSize(), scroll, scrollState, content)
                // Solo si la pantalla desplaza su contenido: Logs usa una
                // tarjeta fija con scroll propio y un degradado la taparía.
                if (scroll) TopFade(scrollState, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * Difuminado bajo el encabezado: mismo degradado que el de la barra de gestos
 * (mismos tramos y opacidades) pero espejado, de opaco arriba a transparente
 * abajo. Su intensidad crece con el desplazamiento (0 en reposo, completa tras
 * recorrer su propio alto), así en reposo no vela la primera tarjeta y solo
 * actúa cuando el contenido de verdad pasa por debajo del encabezado. El
 * alpha se lee dentro de graphicsLayer: se anima sin recomponer. No intercepta
 * toques.
 */
@Composable
private fun TopFade(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val height = if (isLandscape) TopFadeHeightLandscape else TopFadeHeight
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val fade = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = (scrollState.value / heightPx).coerceIn(0f, 1f) }
            .background(
                Brush.verticalGradient(
                    0f to fade.copy(alpha = 0.96f),
                    0.3f to fade.copy(alpha = 0.7f),
                    0.65f to fade.copy(alpha = 0.25f),
                    1f to Color.Transparent
                )
            )
    )
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
private fun ScreenHeader(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    endInset: Dp,
    pullState: PullToRefreshState?,
    refreshing: Boolean
) {
    Column {
        if (pullState != null) {
            PullStretchIndicator(pullState, refreshing)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp + endInset, top = 12.dp, bottom = 8.dp),
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
}

/** Cuánto "estira" como máximo el encabezado al tirar del contenido hacia abajo. */
private val PullStretchMaxHeight = 32.dp

/**
 * Reemplaza al spinner de Material3 (que aparece flotando sobre el
 * contenido y solo se nota una vez que ya se scrolleó hasta arriba de
 * todo). Este vive siempre pegado al encabezado: a medida que se tira del
 * contenido hacia abajo, el encabezado "se estira" (crece este espacio de
 * arriba) y el ícono aparece agrandándose y girando con la propia tracción
 * del dedo; al soltar pasado el umbral, sigue girando solo mientras
 * refresca. [PullToRefreshState.distanceFraction] ya viene en 0f..1f+
 * (puede pasarse de 1 si se tira de más), así que alcanza con acotarlo
 * para el alto/escala (que sí tienen un tope visual), pero NO para la
 * rotación: dejarla sin tope es lo que hace que tirar más haga girar más
 * (más de una vuelta si se tira bastante) en vez de quedarse siempre en
 * un mismo medio giro fijo.
 */
@Composable
private fun PullStretchIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val pull = state.distanceFraction.coerceIn(0f, 1f)
    val pullRaw = state.distanceFraction.coerceAtLeast(0f)

    val heightFraction by animateFloatAsState(
        targetValue = if (refreshing) 1f else pull,
        animationSpec = AppMotion.effects(),
        label = "pullStretchHeight"
    )

    // Un solo Animatable para toda la rotación en vez de dos animaciones
    // independientes (una para "tirando" y un spinner aparte para
    // "refrescando"): así el giro es siempre el mismo movimiento continuo,
    // sin el salto que había antes al pasar de un estado al otro.
    val rotation = remember { Animatable(0f) }

    // Mientras se tira (no refrescando), la rotación seguía al dedo.
    LaunchedEffect(pullRaw, refreshing) {
        if (!refreshing) {
            rotation.animateTo(pullRaw * 360f, animationSpec = AppMotion.effects())
        }
    }
    // Al empezar a refrescar, sigue girando a velocidad constante desde el
    // ángulo exacto donde quedó (rotation.value ya tiene ese valor).
    LaunchedEffect(refreshing) {
        if (refreshing) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 700, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PullStretchMaxHeight * heightFraction),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (heightFraction > 0.05f) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .scale(heightFraction)
                    .rotate(rotation.value % 360f)
            )
        }
    }
}
