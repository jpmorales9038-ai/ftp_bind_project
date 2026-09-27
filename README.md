# RClone FTP Bind

App Jetpack Compose + módulo KernelSU que monta un remoto FTP mediante
**rclone** y lo expone como bind en el almacenamiento interno del dispositivo.

## Estructura

```
app/       App Compose (setup FTP, control de montaje, logs)
module/    Módulo KernelSU (scripts de montaje + binario rclone)
```

## Cómo funciona

1. La app guarda las credenciales FTP en `module/config/rclone.conf` (vía root).
2. `scripts/mount.sh` monta el remoto con `rclone mount --daemon` en un punto
   temporal y luego hace `mount --bind` hacia `/sdcard/FTP`.
3. `scripts/unmount.sh` revierte ambos montajes.
4. `service.sh` remonta automáticamente al boot si el usuario activó
   "Montar al iniciar" desde la app.

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
3. **release**: si el push es un tag `v*` (ej. `v0.1.0`), crea una Release
   en GitHub adjuntando el APK y el zip del módulo.

Para lanzar una versión: `git tag v0.1.0 && git push origin v0.1.0`.

No hace falta tener Android Studio ni el binario de rclone en local para
que el CI compile y empaquete todo.

## Estado

Proyecto inicial / esqueleto funcional. Pendiente: manejo de errores de
conexión FTP, reconexión automática, validación de credenciales antes de
guardar.
