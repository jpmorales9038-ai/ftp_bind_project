<p align="center">
  <img src="docs/banner.svg" alt="RClone FTP Bind" width="100%">
</p>

<p align="center">
  <a href="https://github.com/jpmorales9038-ai/ftp_bind_project/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/jpmorales9038-ai/ftp_bind_project?style=for-the-badge&color=1594A8"></a>
  <a href="https://github.com/jpmorales9038-ai/ftp_bind_project/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/jpmorales9038-ai/ftp_bind_project/build.yml?branch=main&style=for-the-badge&label=build"></a>
  <a href="https://github.com/jpmorales9038-ai/ftp_bind_project/releases"><img alt="Descargas" src="https://img.shields.io/github/downloads/jpmorales9038-ai/ftp_bind_project/total?style=for-the-badge&color=0A4657"></a>
  <img alt="Último commit" src="https://img.shields.io/github/last-commit/jpmorales9038-ai/ftp_bind_project?style=for-the-badge&color=4D616C">
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="arm64" src="https://img.shields.io/badge/arquitectura-arm64-555?style=flat-square">
  <img alt="KernelSU" src="https://img.shields.io/badge/KernelSU-m%C3%B3dulo-orange?style=flat-square">
  <img alt="rclone" src="https://img.shields.io/badge/rclone-mount-1594A8?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
  <img alt="Material 3" src="https://img.shields.io/badge/Material%203-6750A4?style=flat-square&logo=materialdesign&logoColor=white">
</p>

<h3 align="center">Monta servidores FTP y Google Drive como una carpeta más de tu almacenamiento interno.<br>Cualquier app puede usarlos, sin configurar nada en cada una.</h3>

---

## Cómo funciona

```mermaid
flowchart LR
    A["Servidor FTP"] --> R
    B["Google Drive"] --> R
    R["rclone mount<br/>(FUSE)"] --> M["mount --bind"]
    M --> C["/sdcard/FTP<br/>o la carpeta que elijas"]
    C --> D["Galería"]
    C --> E["Reproductores"]
    C --> F["Gestores de archivos"]
    C --> G["Cualquier app"]

    style R fill:#1594A8,color:#fff,stroke:#0A4657
    style M fill:#0A4657,color:#fff,stroke:#0A4657
    style C fill:#CBC1E9,color:#1D1736,stroke:#615A7D
```

Iniciar sesión con Google se hace en el propio teléfono, sin PC:

```mermaid
sequenceDiagram
    participant U as Tú
    participant A as App
    participant R as rclone (local)
    participant G as Google
    U->>A: Iniciar sesión con Google
    A->>R: rclone authorize drive
    R-->>A: URL de autorización
    A->>G: Abre el navegador
    U->>G: Da su consentimiento
    G->>R: Redirige a 127.0.0.1:53682
    R-->>A: Token
    A->>A: Guarda el servidor y comprueba la conexión
```

## Características

### Servidores en una pila de tarjetas

- Cada servidor es una tarjeta; la seleccionada se abre y las demás asoman su franja.
- Tocar una tarjeta elige cuál se monta. Agregar, editar y eliminar desde la misma pantalla.
- Compatible con **FTP** y **Google Drive**.
- Las contraseñas se guardan ofuscadas con `rclone obscure`.
- Al editar, dejar la contraseña vacía conserva la anterior.

### Encuentra tu servidor FTP solo

- Botón **Buscar servidores FTP en mi red**: recorre la subred local y muestra los que responden.
- Sondea el puerto 21 y los que usan las apps de servidor FTP para Android y Termux (2121, 2221 y 2222).
- Usa la red Wi-Fi o Ethernet real aunque haya datos móviles o VPN activos.
- No necesita root: elegir uno rellena Host y Puerto.

### Google Drive sin PC

- Inicio de sesión desde el teléfono con el flujo de autorización de rclone.
- Modo **solo lectura**.
- Interruptor para **permitir archivos marcados como malware** (`acknowledge_abuse`), que Drive bloquea con el error 403 `cannotDownloadAbusiveFile`.
- Montar solo una **carpeta raíz** o una **unidad compartida**.
- **Client ID y Secret propios**, o pegar un token generado en otro equipo.
- Al guardar, comprueba la sesión, la red, el DNS y los certificados listando la raíz de Drive.

### Montaje que se mantiene

- Se monta con `rclone mount` y se expone con `mount --bind` en la **carpeta de destino que elijas** (por defecto `/sdcard/FTP`), con selector de carpetas integrado.
- **Montar al iniciar**: espera a que el almacenamiento esté desbloqueado y reintenta hasta que haya red.
- Un **vigilante** restaura el bind si Android o alguna app lo quita.
- Cambiar de servidor con uno ya montado se hace con un solo botón.
- Caché de disco acotada para Drive.
- **Rendimiento** Equilibrado o Máximo: el modo Máximo usa caché completa también en FTP, lectura anticipada, descargas en paralelo y listados cacheados más tiempo. El **tamaño de caché** es configurable (1 a 50 GB) y se dejan 2 GB libres.

### Instalación y actualizaciones sin fricción

- El zip del módulo **trae la app dentro**: se instala sola al flashear.
- Reflashear **actualiza la app sin perder tus datos**, y la configuración del módulo se conserva.
- Si la instalación silenciosa falla, se reintenta al reiniciar y, como último recurso, abre el instalador del sistema.
- El módulo se actualiza desde el Manager de KernelSU.

### Diseño

- Material 3 con **color dinámico** (Material You) en Android 12 o superior.
- Modo **claro y oscuro** según el sistema, a pantalla completa.
- Esquinas amplias, animaciones con resorte y efecto de desenfoque.
- Icono adaptable con versión monocromática para el tema de íconos.
- Pantallas de **Inicio**, **Servidores**, **Logs** y **Acerca de**, con la versión de la app y de rclone.

### Publicación automática

- Cada compilación exitosa publica un release con el módulo y el APK.
- Los tags `v*` publican una versión con nombre.

## Compatibilidad

| | |
|---|---|
| Root | KernelSU |
| Android | 8.0 o superior |
| Arquitectura | arm64 |
| Remotos | FTP, Google Drive |
| Motor | [rclone](https://rclone.org) |

---

<p align="center">
  <sub>Usa <a href="https://rclone.org">rclone</a> (MIT), <a href="https://github.com/topjohnwu/libsu">libsu</a> (Apache 2.0) y <a href="https://github.com/chrisbanes/haze">Haze</a> (Apache 2.0).</sub>
</p>
