<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_eleuthera_cat.png" width="120" alt="Icono de Eleuthera">
</p>

<h1 align="center">Eleuthera</h1>

<p align="center">Descargador y reproductor de música para Android.</p>

## Qué hace

- **Busca en YouTube** o pega un enlace de vídeo, de YouTube Music o de una playlist.
- **Descarga en segundo plano**, hasta 4 a la vez, en MP3 o en M4A sin recodificar.
- **Las playlists de YouTube** se convierten solas en playlists de la app, en su orden.
- **Reproductor completo**: segundo plano, notificación, pantalla de bloqueo, cola arrastrable, aleatorio y repetición.
- **Biblioteca** por canciones, artistas, álbumes y carpetas, con buscador.
- **Playlists propias** con portada personalizada.
- **Temas**: colores, fuentes, bordes y estilo de botones y tarjetas.

## Instalación

1. Descarga el último `Eleuthera-vX.Y.Z.apk` desde [Releases](../../releases/latest).
2. Ábrelo en el móvil y permite instalar apps de origen desconocido si te lo pide.

Requiere Android 10 o superior. Las actualizaciones se instalan encima sin perder datos.

## Compilar

Abre el proyecto en Android Studio y ejecuta. Para generar una versión publicada,
sube una etiqueta y GitHub Actions compila y publica el APK firmado:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Tecnología

Kotlin, Jetpack Compose, Hilt, Room, DataStore, WorkManager, Media3 (ExoPlayer) y
[youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + FFmpeg).

## Aviso

Proyecto personal. Úsalo solo con contenido propio, con licencia libre o para el que
tengas permiso: descargar contenido de YouTube sin autorización puede ir contra sus
Términos de servicio.
