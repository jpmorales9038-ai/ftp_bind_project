Coloca aquí el binario "rclone" para arm64 (o la arquitectura de tu dispositivo)
antes de empaquetar el módulo. No se versiona en git por tamaño.

Descarga oficial: https://rclone.org/downloads/

También va acá "fusermount3" (binario estático), que Android no trae de
fábrica y rclone lo necesita para montar FUSE aunque se corra como root.
Se descarga automáticamente en CI desde:
https://github.com/till0196/fusermount3-static
