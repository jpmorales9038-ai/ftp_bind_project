# RClone FTP Bind

App Jetpack Compose + módulo KernelSU que monta un remoto FTP mediante
**rclone** y lo expone como bind en el almacenamiento interno del dispositivo.

## Estructura

```
app/       App Compose (setup FTP, control de montaje, logs)
module/    Módulo KernelSU (scripts de montaje + binario rclone)
```

## Cómo funciona

1. El zip del módulo trae el APK embebido (`module/app.apk`). Al flashear
   el módulo desde el Manager de KernelSU (con el sistema arrancado),
   `customize.sh` instala la app automáticamente (`scripts/install_app.sh`:
   `pm`/`cmd package`, por ruta y por stdin): instala la app la
   primera vez y la **actualiza sin perder datos** en cada reflasheo, ya
   que `-r` sobrescribe la instalación existente conservando su config.
   Si el módulo se flashea desde recovery (sistema no arrancado), no hay
   `pm` disponible y el script solo avisa dónde quedó el APK para
   instalarlo a mano.
2. La app guarda cada servidor FTP como una sección `[nombre]` de
   `module/config/rclone.conf` (vía root) y recuerda el seleccionado en
   `module/config/active`. En la pestaña **Servidores** se ven como una pila
   de tarjetas: tocar una la selecciona; ahí mismo se agregan, editan y
   eliminan. Las contraseñas se guardan ofuscadas con `rclone obscure`.
3. `scripts/mount.sh` monta el servidor seleccionado con `rclone mount --daemon` en un punto
   temporal y luego hace `mount --bind` hacia `/sdcard/FTP`.
4. `scripts/unmount.sh` revierte ambos montajes.
5. `service.sh` remonta automáticamente al boot si el usuario activó
   "Montar al iniciar" desde la app.

## Tema

Material 3 con color dinámico (Material You) en Android 12+, paleta propia en
versiones anteriores, modo claro/oscuro según el sistema y edge-to-edge. Las
piezas "expressive" (esquinas grandes, tipografía con más peso, animaciones con
resorte) están hechas con la librería estable; los componentes oficiales de
Material 3 Expressive solo existen en `material3` 1.5.0-alpha.

## Requisitos

- Dispositivo rooteado con **KernelSU**
- Binario `rclone` para la arquitectura del dispositivo, colocado en
  `module/bin/rclone` (ver `module/bin/README.txt`)

## Build (GitHub Actions, sin Android Studio)

El workflow `.github/workflows/build.yml` corre en cada push/PR y en tags `v*`:

1. **build-app**: instala JDK 17 + Android SDK, genera el Gradle wrapper al
   vuelo (no está versionado) y compila `assembleDebug`. Sube el APK como
   artifact.
2. **package-module**: descarga el binario `rclone` (linux-arm64) oficial,
   lo coloca en `module/bin/`, y empaqueta el módulo como zip flasheable de
   KernelSU. Sube el zip como artifact.
3. **release**: en cada push o ejecución manual cuyo build termine bien,
   publica un Release con el zip del módulo y el APK. Un push normal crea
   `build-<n>`; un tag `v*` crea el release con ese nombre. Los PR no publican.

Para una versión con nombre: `git tag v0.1.0 && git push origin v0.1.0`.

No hace falta tener Android Studio ni el binario de rclone en local para
que el CI compile y empaquete todo.

## Estado

Proyecto inicial / esqueleto funcional. Pendiente: manejo de errores de
conexión FTP, reconexión automática, validación de credenciales antes de
guardar.
