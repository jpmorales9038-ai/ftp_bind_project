# Validación necesaria en Android / KernelSU

## Datos de la plataforma
Registrar modelo, RAM, ABI, Android/API, kernel, KernelSU/KernelSU Next, gestor, metamódulo si aplica, estado SELinux, versión/checksum rclone y fusermount3, backend/región y red. No cambiar SELinux a permisivo para esconder errores.

## Antes de instalar
- Respaldar rclone.conf, flags y caché persistente que tenga escrituras pendientes, sin publicar tokens/claves.
- Comprobar firma APK frente a la instalada. Si cambia, no desinstalar hasta tener respaldo y decisión explícita.
- Compilar debug/release, comprobar Manifest, permisos, API y dependencias. Añadir Gradle Wrapper reproducible.
- Ejecutar pruebas locales incluidas; después compilación y pruebas instrumentadas. La batería Python NO valida Kotlin.

## Funcional y datos
1. Instalar/actualizar con misma firma y con firma incompatible: en esta última no debe desinstalar ni borrar datos.
2. Montar FTP, Drive, AWS/Oracle/R2; verificar desde una app sin root. Comprobar namespace/target real, DNS/HTTPS y SELinux denials. Probar con y sin VPN/Private DNS.
3. Probar destino con espacios, symlinks, rutas inválidas y segmentos . / ..; no permitir bind sobre /system, /data/adb ni raíces completas.
4. Lanzar dos montajes, mount+unmount y clear+mount simultáneos: solo una operación debe entrar.
5. Escribir un archivo grande de datos aleatorios. Desconectar la red y desmontar: con cola pendiente debe rechazarse sin borrar caché. Restaurar red, esperar y comparar SHA-256 directamente contra remoto (no solo VFS).
6. Abrir un archivo en una app durante desmontaje: debe rechazar o cerrar limpiamente sin lazy/force. Cambiar servidor no debe continuar si unmount falla.
7. Reiniciar con datos pendientes: recuperar en disco usando mismo remoto y caché antes de permitir limpieza. Simular kill inesperado solo en dispositivo de prueba.
8. Activar RAM: si realmente se utiliza, verificar que crear/editar archivos falla como solo lectura. Si no hay RAM suficiente debe utilizar disco y registrarlo. Metadatos en disco deben bloquear cambio a RAM.
9. Con metadatos VFS, clearCache debe rechazar conservadoramente, aunque estén limpios. No borrar vfsMeta para eludir esto sin verificar la recuperación.
10. Cerrar/cancelar OAuth y prueba de rendimiento: no dejar procesos o tokens temporales. Cancelar/reiniciar precarga repetidamente: no pisar temporales de otra instancia.
11. Crear una carpeta .rclone-bind-test preexistente con archivos y ejecutar benchmark: debe mantenerse intacta.
12. Reboot/autostart antes y después del desbloqueo, red ausente, módulo deshabilitado y reinstalación. Comprobar candados y procesos supervivientes.

## Rendimiento reproducible
- Comparar original y revisión en el mismo dispositivo/red/backend con 5 repeticiones por escenario, sin mezclar cache fría/caliente.
- Medir apertura y carga de la app/juego objetivo, listado de 1k/10k archivos, lecturas secuenciales y aleatorias, archivos pequeños/grandes y subida confirmada remotamente.
- Registrar latencia mediana/p95, MB/s, RSS de rclone y app, CPU, temperatura, consumo de batería, espacio, errores/reintentos y peticiones facturables S3.
- Probar perfiles balanced/max y precarga activada/desactivada. El watcher más lento reduce frecuencia de sondeo pero aumenta demora de recuperación.
- No vaciar page cache global ni hacer drop_caches en el uso normal. Para pruebas frías usar remotos/contenidos nuevos o metodología aislada documentada.
- Criterio de aceptación: cero corrupción/pérdida, firma/permisos correctos, sin OOM ni procesos huérfanos, montaje visible en la app objetivo y mejoras medidas sin regressiones graves. Si falla cualquiera, no publicar como estable.
