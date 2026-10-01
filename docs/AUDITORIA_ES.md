# Auditoría y revisión 1.9.9-review1

## Resultado y alcance
Se revisó el proyecto adjunto 1.9.8 con prioridad en la frontera root, FUSE/bind, datos pendientes, perfiles FTP/Drive/S3, precarga y pruebas de rendimiento. Se entrega una revisión de FUENTES, no una release ni un módulo listo para instalar. No se garantiza el mejor rendimiento posible: depende de dispositivo, kernel, backend, red y carga. No hay mediciones comparativas reales.

El original no contiene APK, rclone, fusermount3, Gradle Wrapper ni un flujo CI dentro del ZIP. Este entorno tampoco tiene Java, Gradle, Kotlin, SDK Android o acceso a internet. No se compiló Kotlin/Compose, no se resolvieron dependencias, no se montó FUSE ni se probó KernelSU/SELinux. Sintaxis POSIX y simulaciones no sustituyen Android mksh/toybox. La revisión debe tratarse como candidata para integración, no como software validado de producción.

## Hallazgos en el original y tratamiento
Las ubicaciones siguientes se refieren a miembros del ZIP original `rclone-ftp-bind_1.9.8.zip`.

| Prioridad | Ubicación original | Problema | Tratamiento |
|---|---|---|---|
| Crítica | module/scripts/unmount.sh:25–51 | Marca desmontado antes de terminar, usa umount -l, mata rclone y descarta tmpfs sin comprobar subidas | Desmontaje no forzado; consulta autenticada vfs/stats; conserva montaje si no puede demostrar cola vacía |
| Crítica | module/scripts/clear_cache.sh:16–26 | mounted:false no demuestra que no haya FUSE/proceso ni escrituras recuperables | Candado común, comprobación de montaje/proceso, rechazo conservador si existen archivos vfsMeta |
| Alta | module/scripts/install_app.sh:43–50 | Desinstala automáticamente con conflicto de firma, perdiendo datos | No desinstalar, no forzar downgrade; pedir resolución manual |
| Alta | module/scripts/env.sh:34–47 | Superpone todo /system/etc para un único archivo DNS | Elimina overlay amplio; bind de resolv.conf solo sobre archivo existente; advierte si sigue ausente |
| Alta | module/scripts/preload.sh:56–85 | Borra temporales antes de adquirir el candado | Adquirir candado antes de borrar, señales terminan y limpieza de hijos |
| Alta | module/scripts/perf_test.sh, sección write | Carpeta fija .rclone-bind-test con limpieza recursiva | Carpeta exclusiva por PID, mkdir sin -p, no borrar una carpeta preexistente, plazos en dd |
| Alta | app/.../root/Conf.kt, validateTargetPath | Permite destinos fuera del almacenamiento, controles/comillas incompatibles con JSON | Validación en UI, persistencia y scripts; verificación de ruta resuelta antes del bind |
| Alta | app/.../BindViewModel.kt, toggleMount | Ignora el resultado de unmount antes de montar otro remoto | Solo montar tras desmontaje exitoso; busy se libera en finally |
| Media | module/scripts/mount.sh:177–179 | Reutiliza FUSE previo aunque remoto/opciones hayan cambiado | Rechaza reutilización; exige desmontar primero |
| Media | module/scripts/preload.sh:66–67 y 156–187 | Redondea MB hacia abajo; conteo+tamaño no prueban identidad/cobertura | Redondeo hacia arriba; no omitir recorridos basándose en huella débil |
| Media | module/scripts/watch.sh:50 | Sondeo cada segundo con dos stat | Intervalo de 5 s, timeout en stat, rebind serializado |
| Media | app/.../net/FtpScanner.kt | 96 hilos, readLine sin tope y actualización por cada puerto; fallback podía elegir móvil/VPN; /31 expandida artificialmente | 32 hilos, banner 1 KiB y deadline, progreso agrupado, fallback LAN, sin expandir /31–/32 |
| Media | app/.../root/RootShell.kt | Valor de contraseña como argumento de obscure, readConf sin comprobar fallo y escritura INI sin control de saltos | obscure por stdin, error de lectura no sobreescribe, validación INI, guardado de perfiles sincronizado |
| Media | app/src/main/AndroidManifest.xml | allowBackup=true innecesario para una app con funciones privilegiadas | Backup deshabilitado |
| Media | app/build.gradle.kts | Clave debug pública compartida usada para actualizaciones | Se conserva para compatibilidad debug; añade firma release privada opcional por entorno. Cambiar firma exige migración manual |

## Arquitectura de la revisión
- `common.sh`: permisos privados, validación, namespace con guardia contra reentrada, candado de operaciones, timeout independiente del comando Android timeout, RC autenticado.
- `status.sh`: verifica FUSE antes de devolver un estado montado; no considera status.json prueba suficiente de existencia.
- RC escucha exclusivamente en 127.0.0.1:55783, usuario rclonebind y secreto aleatorio de 32 bytes guardado con 0600. No se usa rc-no-auth. Si el puerto está ocupado, el montaje debe fallar, no conectarse silenciosamente a otro servidor.
- El desmontaje espera hasta aproximadamente 60 s por cola/archivos abiertos, aparte de timeouts de las consultas. Inspecciona uploadsQueued, uploadsInProgress, erroredFiles e inUse. Respuesta incompleta o incompatible = rechazo. La estructura real de vfs/stats y cierre de rclone deben comprobarse con el binario elegido.
- No hay umount lazy, force ni kill global de rclone. Esto puede impedir desmontar cuando haya apps usando archivos: cerrar esas apps y reintentar es deliberadamente preferible a perder datos.
- La caché RAM activa impone `--read-only`. Si hay metadatos VFS en disco, se rechaza la activación de RAM de manera conservadora. Esta protección puede rechazar también cachés limpias: no se interpreta el esquema de vfsMeta para evitar falsos negativos peligrosos.
- La limpieza de caché también es conservadora: puede negarse después de un desmontaje limpio si rclone conserva metadatos. No se añadió un borrado forzado. La recuperación/limpieza selectiva de entradas limpias queda pendiente de implementar contra una versión fijada de rclone.
- Candados obsoletos fallan de forma segura y se limpian al inicio del servicio tras un reboot. No se asume que kill -0/PID basta para recuperar un candado en presencia de reutilización de PID.
- Rotación limitada del log al próximo montaje si supera 4 MiB, reteniendo 2000 líneas. No limita crecimiento durante un montaje muy largo.
- Se retiran updateJson de esta revisión para que una actualización del proyecto original no sobrescriba silenciosamente los cambios. App y módulo pasan a versionCode 47 / 1.9.9-review1.

## Rendimiento: decisiones y límites
Se mantienen buffers moderados, transfers/checkers y perfiles existentes. No se aumenta indiscriminadamente la concurrencia ni se modifican parámetros globales del kernel, scheduler, SELinux, CPU, TCP o drop_caches.

Las mejoras buscan reducir trabajo y contención, no demostrar más MB/s: watcher menos frecuente (reacción más lenta), menos hilos de escaneo (puede tardar más), progreso menos ruidoso y precarga de prioridad baja con 1–2 workers bajo presión de memoria. Contabilizar por MB redondeados puede infraprecargar colecciones de miles de archivos pequeños, pero evita superar el presupuesto por tratarlos como cero.

La caché completa mejora relecturas; no acelera una primera descarga limitada por el enlace. El límite VFS de espacio es blando: archivos abiertos y subidas pendientes pueden superarlo. RAM limitada por tmpfs no constituye reserva garantizada contra OOM. Para contenido escribible, usar caché persistente en disco, no RAM.

La prueba incorporada mide principalmente filesystem/VFS; escribir/releer rápidamente no certifica subida remota ni integridad después de reboot. Las lecturas supuestamente frías pueden ya estar cacheadas. No debe presentarse su resultado como benchmark fiable de internet o del proveedor.

## Pruebas ejecutadas
`python3 tests/test_review.py`: 15 métodos de prueba, resultado OK. Incluyen subcasos de rutas y matriz de 6 perfiles. `dash -n`: 16 scripts sin errores de sintaxis. Pruebas de namespace real, mount, binarios, compilación, firma, Google OAuth y SELinux: NO ejecutadas.

Se comprobaron validación de rutas (incluidos saltos de línea), timeout y códigos, exclusión/limpieza de candados, secreto RC y permisos, parseo literal de remotos/CRLF/rangos, perfiles con rclone simulado, conflicto de firma, temporales de precarga bajo concurrencia, rechazo de borrado con metadatos y desmontaje simulado con cola vacía/pendiente. Las aserciones de Kotlin son textuales, no compilación ni pruebas de comportamiento Android.

## Pendientes y riesgos que NO se deben ocultar
1. **Build y dependencias**: verificar disponibilidad y compatibilidad de AGP 8.13.0, Kotlin 2.2.21, Compose BOM 2026.04.01, Material3 1.5.0-alpha18 y haze 1.6.10. No se han reemplazado a ciegas ni se ha confirmado su publicación.
2. **DNS**: la resolución Go sigue dependiendo de resolv.conf. Si no existe, puede requerir montaje systemless/metamódulo. Los DNS públicos originales no respetan necesariamente Private DNS, VPN o split DNS. Configurar según la red antes de usarlo; no hay integración netd implementada.
3. **Namespaces y almacenamiento**: entrar en PID 1 no garantiza visibilidad en apps con namespaces propios/scoped storage. Fallback /data/media/0 no prueba visibilidad en todos los exploradores ni juegos. Probar con las apps objetivo, no únicamente adb root.
4. **Estado**: status.sh valida FUSE, pero no certifica la accesibilidad del remoto, bind visible en otra app ni opciones aplicadas. Si el bind falla después de crear FUSE puede quedar FUSE huérfano y requerir intervención controlada.
5. **Concurrencia**: candado serializa mount/unmount/clear y rebind. No es una base transaccional compartida por OAuth y cambios externos de rclone.conf. La sincronización JVM tampoco impide escritura externa/refresh de token concurrente.
6. **Precarga**: listas basadas en líneas no soportan nombres de archivo con newline; enumeración de find todavía puede ser costosa; recorrer para validar no detecta de inmediato cambios mientras dir-cache-time siga vigente. No se afirma cobertura absoluta de la caché.
7. **Timeouts**: TERM no termina procesos bloqueados en E/S ininterrumpible; no se fuerza KILL por seguridad. Se siguen usando pgrep/pkill de patrones específicos en algunos controles de auxiliares y se deben validar en toybox.
8. **Memoria y batería**: coste por archivo abierto, concurrencia real y presión de RAM no están medidos. Control adaptativo de foreground/metered network no implementado; la precarga puede competir con un juego o consumir datos móviles.
9. **RC y datos**: comprobar campos exactos contra la versión real, puerto, permisos y proceso daemon. Las credenciales se protegen frente a usuarios no root, no frente a root. No hay defensa contra escritura nueva concurrente entre la consulta final y el desmontaje; umount no forzado ayuda, pero no reemplaza cerrar clientes escritores.
10. **Seguridad de transporte**: FTP sin TLS envía credenciales/contenido en claro. El backend puede conservar ajustes tls manuales, pero no se agregó un flujo FTPS/SFTP completo. Evaluar antes de usar fuera de una LAN confiable.
11. **Firma**: el debug.keystore original se conserva para no romper updates debug y NO es una identidad segura de producción. No se cambió applicationId, por lo que una firma release distinta entrará en conflicto; respaldar y migrar conscientemente.
12. **UI/lifecycle**: no hay prueba instrumentada de cancelación OAuth, recreación, procesos muertos o versiones Android recientes. No se eliminaron alfa/blur sin comprobar el diseño.

## Compilación y empaquetado
1. Lee `VALIDACION_ANDROID_ES.md` y respalda configuración/caché persistente con escrituras pendientes.
2. Instala un entorno Android de desarrollo JDK 17, SDK 36 y Gradle compatible con AGP. El ZIP no trae Wrapper: generarlo y versionarlo con checksum de la distribución aprobada.
3. Resuelve dependencias y ejecuta `gradle :app:assembleDebug` o release tras integrar Wrapper. No se ha ejecutado aquí.
4. Release opcional: variables RCB_KEYSTORE, RCB_STORE_PASSWORD, RCB_KEY_ALIAS, RCB_KEY_PASSWORD. Nunca incluir esa clave privada en el repo/ZIP. No se añadió shrink/minify sin validarlo.
5. Obtén rclone y fusermount3 verificados para la arquitectura del dispositivo y conserva versión/checksum/origen. No se ha descargado ninguno. Coloca los binarios en module/bin y APK en module/app.apk.
6. Empaqueta el contenido de module como raíz del ZIP instalable; el ZIP de fuentes entregado NO tiene esa estructura ni binarios. customize.sh abortará un módulo incompleto.
7. Usa una instalación de prueba y valida todo el plan antes de considerarlo estable. La revisión no publica releases en el repositorio original.
