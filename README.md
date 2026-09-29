# RClone FTP Bind

App Jetpack Compose + módulo KernelSU que monta un remoto (**FTP** o **Google Drive**)
mediante **rclone** y lo expone como bind en el almacenamiento interno del dispositivo.

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
2. La app guarda cada servidor (FTP o Google Drive) como una sección `[nombre]` de
   `module/config/rclone.conf` (vía root) y recuerda el seleccionado en
   `module/config/active`. En la pestaña **Servidores** se ven como una pila
   de tarjetas: tocar una la selecciona; ahí mismo se agregan, editan y
   eliminan. Las contraseñas se guardan ofuscadas con `rclone obscure`. Al
   agregar un servidor, un botón "Buscar servidores FTP en mi red" barre la
   subred local (puerto 21) con sockets normales de la app —sin root— y
   deja elegir uno para llenar Host/Puerto automáticamente
   (`app/.../net/FtpScanner.kt`).
   **Google Drive**: en el formulario se elige el tipo "Google Drive" y se toca
   "Iniciar sesión con Google". La app lanza `rclone authorize drive`
   (`scripts/drive_auth.sh`, con root) y abre la URL en el navegador del
   teléfono; Google redirige a `127.0.0.1:53682`, donde escucha rclone en el
   propio dispositivo, y el token queda guardado en la sección del remoto
   (`type = drive`, `scope`, `token`). No hace falta un PC. Opciones: solo
   lectura (`drive.readonly`), carpeta raíz o unidad compartida, Client
   ID/Secret propios y pegar un token generado en un PC. Al guardar se lista
   la raíz de Drive (`scripts/check_remote.sh`) para confirmar que la
   sesión, la red, el DNS y los certificados funcionan. El cliente OAuth
   de Google lo trae la app (se inyecta al compilar desde los secrets
   `GDRIVE_CLIENT_ID` y `GDRIVE_CLIENT_SECRET` de GitHub Actions, o desde
   `gdriveClientId` / `gdriveClientSecret` en `gradle.properties` local; nunca
   en el repo): el usuario solo da su consentimiento. Sin secrets se usa el
   cliente compartido de rclone. En "Opciones avanzadas" cada usuario puede
   poner el suyo. Para crear el cliente: Google Cloud Console, proyecto con la
   API de Drive habilitada, cliente OAuth tipo "Aplicación de escritorio" y
   pantalla de consentimiento publicada "En producción" (en "Testing" el token
   caduca a los 7 días). Sin verificación de Google, el scope `drive`
   (restringido) permite hasta 100 usuarios nuevos y muestra la pantalla
   "app no verificada".
3. `scripts/mount.sh` monta el servidor seleccionado con `rclone mount --daemon` en un punto
   temporal y luego hace `mount --bind` hacia la carpeta de destino
   (`module/config/target_path`, editable desde **Inicio**; por defecto
   `/sdcard/FTP`).
4. `scripts/unmount.sh` revierte ambos montajes, usando la ruta que quedó
   realmente montada (guardada en `status.json`) por si el usuario cambió
   la carpeta de destino después de montar sin haber vuelto a montar.
   Los scripts comparten `scripts/env.sh` (HOME, PATH y `SSL_CERT_DIR` con los
   certificados de Android; sin eso el rclone estático no valida HTTPS). Para
   Drive, `mount.sh` usa `--vfs-cache-mode full` (caché acotada a 1 GB) y el
   módulo agrega `system/etc/resolv.conf` (rclone resuelve DNS leyéndolo y
   Android no lo trae): tras flashear por primera vez hay que **reiniciar**.
   Si el dispositivo ya tiene uno con nameservers, el módulo no lo pisa. Como
   respaldo (p. ej. KernelSU sin metamódulo), `env.sh` monta en runtime un
   overlay de `/system/etc` con un `resolv.conf` propio.
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
