package com.rclonebind.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Tarjeta de sección: superficie muy redondeada con encabezado
 * (icono + título), subtítulo opcional y el contenido debajo.
 * Todos los colores salen del esquema del sistema.
 *
 * Con [expandable] el encabezado se puede tocar para plegar/desplegar el
 * contenido (útil para tarjetas largas); el subtítulo, si hay, siempre
 * queda visible como resumen aunque esté plegada. [initiallyExpanded]
 * solo importa si [expandable] es true.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandable: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(!expandable || initiallyExpanded) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevronRotation")

    Surface(
        color = scheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = if (expandable) {
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = !expanded }
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (expandable) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Contraer" else "Expandir",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * Opción seleccionable tipo "Curve / Sliders": icono en un círculo más la
 * etiqueta. Seleccionada: relleno primaryContainer, contorno primary y el
 * círculo en primary. Sin seleccionar: relleno tenue y círculo neutro.
 */
@Composable
fun OptionTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        label = "tileContainer"
    )
    val outline by animateColorAsState(
        if (selected) scheme.primary else Color.Transparent, label = "tileOutline"
    )
    val badge by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHighest,
        label = "tileBadge"
    )
    val badgeContent by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "tileBadgeContent"
    )

    Row(
        modifier = modifier
            .height(72.dp)
            .clip(shape)
            .background(container)
            .border(2.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(badge),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = badgeContent)
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1
        )
    }
}

/** Iconos que no están en material-icons-core, dibujados con los paths estándar. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black)
        ).build()

    /**
     * Logo de la app (mismo dibujo que el icono del launcher): carpeta blanca
     * con una nube dentro. Va con colores propios: usar con Image, no con Icon.
     */
    val Logo: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "Logo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes("M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"),
            fill = SolidColor(Color.White)
        ).addPath(
            pathData = addPathNodes("M9.3,17.4H14.9A2.3,2.3 0 0 0 15.086,12.808A3,3 0 0 0 9.114,12.808A2.3,2.3 0 0 0 9.3,17.4Z"),
            fill = SolidColor(Color(0xFF0F6E80))
        ).build()
    }

    val Folder: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        icon(
            "Folder",
            "M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"
        )
    }

    val Cloud: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        icon(
            "Cloud",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z"
        )
    }

    val Bolt: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        icon(
            "Bolt",
            "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66 0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z"
        )
    }

    val Dns: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        icon(
            "Dns",
            "M20,13H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1v-6c0,-0.55 -0.45,-1 -1,-1zM7,19c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM20,3H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1zM7,9c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"
        )
    }

    val Download: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        icon(
            "Download",
            "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"
        )
    }

    /**
     * Triángulo de tres colores inspirado en la paleta de marca de Google
     * Drive (azul/verde/amarillo) para distinguir de un vistazo las
     * tarjetas de perfiles de Drive. No es una réplica exacta del
     * logotipo oficial, es una forma propia con esos tres colores. Va con
     * Image, no con Icon: Icon fuerza un solo tinte y perdería los colores.
     */
    val DriveLogo: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "DriveLogo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes("M12,4 L3,19 L12,14 Z"),
            fill = SolidColor(Color(0xFF00AC47))
        ).addPath(
            pathData = addPathNodes("M12,4 L12,14 L21,19 Z"),
            fill = SolidColor(Color(0xFF2684FC))
        ).addPath(
            pathData = addPathNodes("M3,19 L21,19 L12,14 Z"),
            fill = SolidColor(Color(0xFFFFBA00))
        ).build()
    }
}

/** Azul de marca de Drive, para acentos y fondos de tarjeta (no solo el logo). */
val DriveBrandBlue = Color(0xFF2684FC)
