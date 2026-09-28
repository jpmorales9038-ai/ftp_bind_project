package com.rclonebind.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Un servidor FTP encontrado en la red local (puerto 21 abierto). */
data class FoundFtpServer(val ip: String, val banner: String?)

private const val CONNECT_TIMEOUT_MS = 400
private const val BANNER_TIMEOUT_MS = 500
private const val MAX_CONCURRENCY = 48

/** Cuántos hosts se recorren como máximo (una /24), aunque la máscara real
 *  de la red sea más amplia; evita que el escaneo tarde minutos en redes
 *  grandes o corporativas. */
const val FTP_SCAN_HOST_COUNT = 254

/**
 * Busca la IP local (no loopback) con la que el teléfono está en la red,
 * para saber qué subred barrer. No pide permisos especiales: NetworkInterface
 * es una API de Java estándar, no un servicio del sistema restringido como
 * WifiManager (que además necesita ubicación en versiones recientes de
 * Android para dar la IP completa).
 *
 * getNetworkInterfaces() declara SocketException; sin capturarla acá, un
 * fallo (por ejemplo justo al cambiar de red) se colaba como excepción no
 * controlada en la corrutina del escaneo y la dejaba trabada en "Cancelar
 * búsqueda…" para siempre, sin ningún aviso.
 */
private fun localIPv4(): Inet4Address? = try {
    NetworkInterface.getNetworkInterfaces()?.asSequence()
        ?.filter { it.isUp && !it.isLoopback }
        ?.flatMap { it.interfaceAddresses.asSequence() }
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress }
} catch (e: Exception) {
    null
}

/**
 * Intenta conectar al puerto 21 de [ip]. Si el socket conecta ya se cuenta
 * como candidato a FTP; el saludo del servidor ("220 ...") se guarda aparte
 * solo para mostrarlo en la lista, no es obligatorio recibirlo a tiempo.
 */
private fun probeFtp(ip: String): FoundFtpServer? = try {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, 21), CONNECT_TIMEOUT_MS)
        val banner = try {
            socket.soTimeout = BANNER_TIMEOUT_MS
            socket.getInputStream().bufferedReader().readLine()?.trim()
        } catch (e: Exception) {
            null
        }
        FoundFtpServer(ip, banner)
    }
} catch (e: Exception) {
    null
}

/**
 * Recorre la subred /24 de la IP local en busca de servidores FTP.
 * [onProgress] se llama desde varias corrutinas a la vez (una por host
 * revisado) solo para actualizar un contador en la UI; no garantiza orden.
 * Devuelve la lista vacía si el teléfono no está en ninguna red (por
 * ejemplo, con los datos móviles apagados y sin Wi-Fi).
 */
suspend fun scanForFtpServers(onProgress: (checked: Int, total: Int) -> Unit): List<FoundFtpServer> =
    withContext(Dispatchers.IO) {
        val local = localIPv4() ?: return@withContext emptyList()
        val bytes = local.address
        val base = "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}."
        val selfLast = bytes[3].toInt() and 0xFF

        val checked = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)

        val results = coroutineScope {
            (1..FTP_SCAN_HOST_COUNT).map { host ->
                async {
                    semaphore.withPermit {
                        val found = if (host == selfLast) null else probeFtp(base + host)
                        onProgress(checked.incrementAndGet(), FTP_SCAN_HOST_COUNT)
                        found
                    }
                }
            }.awaitAll()
        }

        results.filterNotNull().sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
    }
