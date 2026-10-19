<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_eleuthera_cat.png" width="140" alt="Icono de Eleuthera">
</p>

<h1 align="center">Eleuthera</h1>

<p align="center">
  Descarga música de YouTube, organízala y escúchala sin conexión.<br>
  Trae tus playlists de Spotify, Qobuz y otros servicios en un par de toques.
</p>

<p align="center">
  <a href="https://github.com/ErMichoss/Eleuthera/releases/latest"><img src="https://img.shields.io/github/v/release/ErMichoss/Eleuthera?label=descargar&style=for-the-badge" alt="Última versión"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 10 o superior">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin y Jetpack Compose">
</p>

> *Eleuthera* (ἐλευθέρα) significa «libre» en griego antiguo.

---

## Índice

- [Funciones](#funciones)
- [Instalación](#instalación)
- [Primeros pasos](#primeros-pasos)
- [Buscar y descargar](#buscar-y-descargar)
- [Importar playlists de Spotify, Qobuz y otros](#importar-playlists-de-spotify-qobuz-y-otros)
- [Biblioteca](#biblioteca)
- [Reproductor](#reproductor)
- [Playlists](#playlists)
- [Personalización](#personalización)
- [Cuenta de YouTube (cookies)](#cuenta-de-youtube-cookies)
- [Dónde se guarda todo](#dónde-se-guarda-todo)
- [Solución de problemas](#solución-de-problemas)
- [Para desarrolladores](#para-desarrolladores)
- [Créditos](#créditos)
- [Aviso legal](#aviso-legal)

---

## Funciones

| | |
|---|---|
| 🔎 **Búsqueda** | Busca en YouTube o pega un enlace de vídeo, de YouTube Music o de una playlist. |
| ⬇️ **Descargas** | En segundo plano, hasta 4 a la vez, con notificación de progreso. En MP3 o en M4A sin recodificar. |
| 📥 **Importación** | Trae tus playlists de Spotify, Qobuz, Apple Music, Deezer, Tidal… a partir de un archivo CSV o de texto. |
| 📚 **Biblioteca** | Por canciones, artistas, álbumes y carpetas, con buscador. Puede incluir otras carpetas del móvil. |
| ▶️ **Reproductor** | Segundo plano, notificación, pantalla de bloqueo, auriculares y Bluetooth. Cola arrastrable, aleatorio y repetición. |
| 🎶 **Playlists** | Propias o creadas automáticamente al descargar una playlist de YouTube. Con portada personalizada. |
| 🎨 **Personalización** | Colores, tipografía, bordes y estilo de botones y tarjetas, con temas listos para usar. |
| 🔁 **Sin duplicados** | Una canción que está en varias playlists se descarga una sola vez. |

---

## Instalación

### Android

1. En el móvil, abre la [última versión](https://github.com/ErMichoss/Eleuthera/releases/latest) y descarga el archivo `Eleuthera-vX.Y.Z.apk`.
2. Ábrelo. Si Android lo pide, permite **instalar aplicaciones de origen desconocido** para tu navegador o gestor de archivos.
3. Pulsa **Instalar**.

**Requisitos:** Android 10 o superior. La app ocupa bastante porque incluye el motor de descarga completo (yt-dlp, Python y FFmpeg).

### Actualizar

Descarga el APK de la versión nueva y ábrelo: se instala encima de la anterior **sin perder** playlists, ajustes ni música.

> ⚠️ Si alguna vez instalaste la app desde Android Studio, esa copia está firmada con otra clave y Android no dejará actualizarla. Desinstálala primero: los MP3 se conservan en `Música/Eleuthera`, pero se pierden las playlists y los ajustes.

### Windows, Linux y macOS

En la [última versión](https://github.com/ErMichoss/Eleuthera/releases/latest) descarga el instalador de tu sistema:

| Sistema | Archivo | Cómo se instala |
|---|---|---|
| **Windows 10/11** | `…-windows-x64.msi` | Ábrelo. Se instala para tu usuario, sin pedir administrador. |
| **Linux** (Debian, Ubuntu, Mint…) | `…-linux-amd64.deb` | `sudo apt install ./Eleuthera-vX.Y.Z-linux-amd64.deb` |
| **macOS** con M1 o posterior | `…-mac-arm64.dmg` | Abre el `.dmg` y arrastra Eleuthera a Aplicaciones. |
| **macOS** con Intel | `…-mac-x64.dmg` | Igual que el anterior. |

No hace falta instalar Java: va incluido. La primera vez que se abre, la app descarga sola yt-dlp, FFmpeg y Deno; tarda un poco según la conexión.

> 🍎 La versión de macOS no está firmada por Apple. La primera vez, haz clic derecho sobre Eleuthera > **Abrir**. Si dice que la app «está dañada», ejecuta en Terminal: `xattr -dr com.apple.quarantine /Applications/Eleuthera.app`

**Actualizar:** la app avisa cuando hay una versión nueva. El instalador nuevo se pone encima del anterior sin perder nada.

---

## Primeros pasos

La app pide dos permisos, opcionales pero recomendables:

| Permiso | Para qué |
|---|---|
| **Notificaciones** | Ver el progreso de las descargas y los controles del reproductor. |
| **Música y audio** | Leer tu biblioteca. Sin él, la pestaña Biblioteca queda vacía. |

La primera búsqueda tarda unos segundos más: la app prepara el motor y descarga la última versión de yt-dlp. Esto se repite en cada arranque para que las descargas sigan funcionando cuando YouTube cambia algo.

---

## Buscar y descargar

En la pestaña **Buscar** puedes escribir:

- **Texto** (`queen bohemian rhapsody`): muestra los 20 primeros resultados de YouTube.
- **Un enlace de vídeo** de `youtube.com`, `youtu.be` o `music.youtube.com`.
- **Un enlace de playlist**: lista todas sus canciones y aparece el botón **Descargar todo**.

Pulsa el icono ⬇️ de una canción para descargarla. Cada fila muestra su estado: *En cola*, porcentaje, *Guardado* o el error con un botón para reintentar.

- **Segundo plano:** las descargas siguen aunque cierres la app, y se pueden cancelar desde la notificación.
- **Paralelo:** como máximo 4 a la vez; el resto espera en cola.
- **Reintentos:** si una falla, se reintenta sola dos veces antes de marcarla como fallida.
- **Playlists de YouTube:** al descargar una playlist entera se crea automáticamente una playlist en la app con el mismo nombre y en el mismo orden.

### Formato de audio

En **Ajustes > Formato de descarga**:

| Formato | Ventajas | Inconvenientes |
|---|---|---|
| **MP3 (compatible)** | Lo reproduce cualquier aparato. | Se recodifica: tarda más y pierde algo de calidad. |
| **M4A (sin recodificar)** | Mucho más rápido; guarda el audio AAC tal cual lo sirve YouTube. | Algún reproductor muy antiguo no lo abre. |

El cambio afecta solo a las descargas nuevas.

---

## Importar playlists de Spotify, Qobuz y otros

Eleuthera no descarga el audio de Spotify ni de Qobuz, que está protegido. Lo que hace es leer **la lista de canciones** (título, artista y duración), buscar cada una en YouTube, elegir la versión correcta y descargarla, creando la playlist con el mismo nombre y orden.

### Paso 1: exporta tu lista a un archivo

<details open>
<summary><b>Spotify, con Exportify</b> (gratis, recomendado)</summary>

1. Entra en [exportify.app](https://exportify.app) desde el ordenador o el móvil.
2. Inicia sesión con tu cuenta de Spotify.
3. Verás todas tus playlists, incluida **Liked Songs** (Canciones que te gustan).
4. Pulsa **Export** en la que quieras (también hay una opción para exportarlas todas).
5. Obtienes un archivo `.csv` por playlist.

</details>

<details>
<summary><b>Qobuz, Apple Music, Deezer, Tidal, Amazon Music, YouTube Music…, con TuneMyMusic</b></summary>

1. Entra en [tunemymusic.com](https://www.tunemymusic.com) y empieza una transferencia.
2. Como origen, elige tu servicio (por ejemplo, **Qobuz**) e inicia sesión.
3. Selecciona las playlists que quieras pasar.
4. Como destino, elige **Export to file** y el formato **CSV**.
5. Descarga el archivo.

Si exportas varias playlists de golpe, el CSV las incluye todas con una columna *Playlist name*: Eleuthera crea una playlist por cada una automáticamente.

> La versión gratuita de TuneMyMusic limita el número de canciones por transferencia. [Soundiiz](https://soundiiz.com) es una alternativa que también exporta a CSV.

</details>

<details>
<summary><b>Una lista escrita a mano</b></summary>

Crea un archivo `.txt` con una canción por línea, en formato `Artista - Título`:

```text
Queen - Bohemian Rhapsody
Linkin Park - The Emptiness Machine
Extremoduro - La vereda de la puerta de atrás
```

Las líneas que empiezan por `#` se ignoran. Si una línea no lleva ` - `, se busca tal cual.

</details>

### Paso 2: impórtala en Eleuthera

1. Pasa el archivo al móvil (USB, Drive, Telegram, correo…).
2. En **Buscar**, pulsa **Importar lista de Spotify, Qobuz… (CSV o TXT)** y elige el archivo.
3. Revisa el resumen: número de canciones y nombre de la playlist, que puedes cambiar. Si el archivo trae varias playlists, se muestran todas.
4. Pulsa **Importar**.

El progreso aparece en una tarjeta en **Buscar** y en la notificación. Puedes importar varios archivos a la vez.

### Cómo elige la canción correcta

Para cada canción, la app busca `Artista - Título` en YouTube y compara los 5 primeros resultados:

- ✅ **Prioriza** los que tienen una duración parecida a la original y los canales oficiales de YouTube Music (`Artista - Topic`).
- ❌ **Descarta** directos, covers, remixes, karaoke, versiones aceleradas o ralentizadas, salvo que el título original también lo sea.
- 🚫 **Si nada se parece** (más de 1 minuto y medio de diferencia), no descarga nada en lugar de bajar algo equivocado.

Al terminar, **Ver no encontradas** muestra las que no pudo emparejar, para que las busques a mano.

### Duplicados

- Si una canción está en **varias playlists**, se descarga **una sola vez** y aparece en todas.
- Si ya estaba **descargada**, solo se añade a la playlist.
- Si una lista la repite, solo cuenta una vez.
- Puedes **volver a importar** el mismo archivo (por ejemplo, tras cancelar): no se duplica nada.

> Solo se reconocen las canciones descargadas desde que existe esta función. Las bajadas con versiones anteriores de la app no guardaban de qué vídeo venían y podrían descargarse otra vez.

### Consejos

- **Ten paciencia:** la búsqueda va a unos 3-4 segundos por canción para no provocar el bloqueo de YouTube. 200 canciones tardan unos 12-15 minutos en buscarse, más las descargas.
- **Para listas grandes**, deja el móvil cargando y con wifi, y carga tus [cookies](#cuenta-de-youtube-cookies).
- **Si muchas búsquedas empiezan a fallar seguidas**, YouTube está limitando las peticiones: cancela, espera un rato y vuelve a importar.
- **Compras de Qobuz:** si tienes álbumes **comprados** (no en streaming), esos archivos FLAC son tuyos y sin protección. Cópialos al móvil y añade su carpeta en **Ajustes > Carpetas de la biblioteca**: tendrás la calidad original sin descargar nada.

### Formatos de archivo aceptados

Eleuthera reconoce las columnas por su nombre, así que funciona con la mayoría de exportadores:

| Dato | Nombres de columna reconocidos |
|---|---|
| Título *(obligatorio)* | `Track Name`, `Title`, `Song`, `Name`… |
| Artista | `Artist Name(s)`, `Artist Name`, `Artists`, `Artist` |
| Duración | `Duration (ms)`, `Duration` (milisegundos, segundos o `m:ss`) |
| Playlist | `Playlist name`, `Playlist` |

Separadores admitidos: coma, punto y coma o tabulador.

---

## Biblioteca

La pestaña **Biblioteca** muestra tu música en cuatro pestañas: **Canciones**, **Artistas**, **Álbumes** y **Carpetas**. Toca un artista, álbum o carpeta para ver sus canciones.

- **Buscador:** filtra por título, artista o álbum en todas las pestañas.
- **Menú ⋮ de cada canción:** *Reproducir a continuación*, *Añadir a la cola*, *Añadir a playlist* y *Eliminar del dispositivo*.
- **Actualización automática:** las descargas nuevas aparecen solas.

### Otras carpetas

Por defecto solo se muestra `Música/Eleuthera`. Para incluir música que ya tuvieras en el móvil, ve a **Ajustes > Carpetas de la biblioteca > Añadir carpeta**. Solo se admiten carpetas del almacenamiento interno, no de la tarjeta SD.

### Eliminar canciones

**Eliminar del dispositivo** borra el archivo definitivamente. Si la canción no la descargó Eleuthera (o reinstalaste la app), Android pedirá confirmación.

---

## Reproductor

Toca cualquier canción para reproducirla: el resto de la lista queda en la cola.

- **Mini-reproductor** sobre la barra inferior; tócalo para abrir la pantalla completa.
- **Pantalla completa:** carátula, barra de progreso, anterior/siguiente, aleatorio y repetición (todo / una / desactivado).
- **Cola:** el icono de arriba a la derecha la muestra. Arrastra el asa ≡ para reordenar, ✕ para quitar y toca una canción para saltar a ella.
- **Segundo plano:** sigue sonando con la app cerrada, con controles en la notificación, la pantalla de bloqueo, los auriculares y el coche por Bluetooth.
- **Pausa automática** al recibir una llamada o desconectar los auriculares.

---

## Playlists

En la pestaña **Playlists** se ven todas como tarjetas con portada.

| Acción | Cómo |
|---|---|
| Crear | Botón **Nueva**, o desde el menú ⋮ de una canción > *Añadir a playlist* |
| Añadir canciones | Menú ⋮ de cualquier canción en Biblioteca |
| Reproducir | **Reproducir** o **Aleatorio** en la cabecera |
| Reordenar | Arrastra el asa ≡ de cada canción |
| Quitar una canción | ✕ junto a la canción (el archivo no se borra) |
| Renombrar | Icono ✏️ en la cabecera |
| Cambiar portada | Toca la portada > *Elegir imagen*; *Volver al mosaico automático* la quita |
| Eliminar | Icono 🗑️ en la cabecera (las canciones siguen en tu biblioteca) |

La portada automática es un mosaico con las carátulas de las 4 primeras canciones.

Las playlists se crean solas al descargar una playlist de YouTube o al importar una lista.

---

## Personalización

En **Ajustes**, con vista previa en directo:

- **Temas rápidos:** Eleuthera, Claro, AMOLED, Océano, Atardecer y Papel.
- **Colores:** primario, secundario, acento y fondo. Elige de la paleta o toca 🎨 para un color libre (tono, saturación, brillo o código hexadecimal). Los demás colores se calculan solos para que el texto siempre se lea bien.
- **Tipografía:** Sans, Serif, Mono, Cursiva, Inter, Montserrat, Nunito y Playfair.
- **Bordes redondeados:** de 0 a 28 dp.
- **Estilo de botones y tarjetas:** Plano, Elevado, Contorno y Neumórfico.
- **Restablecer** vuelve al tema original.

---

## Cuenta de YouTube (cookies)

Las cookies **no son obligatorias**. Sirven cuando YouTube responde *«Sign in to confirm you're not a bot»*, para vídeos con restricción de edad y para importaciones grandes.

> ⚠️ Usar cookies de una cuenta con muchas descargas puede hacer que YouTube la bloquee temporal o permanentemente. **Usa una cuenta secundaria**, no la principal.

### Opción A: iniciar sesión en la app (más fácil)

1. **Ajustes > Cuenta de YouTube > Iniciar sesión en YouTube**.
2. Entra en tu cuenta y pulsa **Guardar sesión**.

La app comprueba que la sesión está iniciada y borra después las cookies del navegador interno, para que esa sesión solo la use el motor de descarga.

Si Google muestra *«Este navegador o aplicación puede no ser seguro»*, usa la opción B.

### Opción B: importar un archivo

1. En el ordenador, instala la extensión **Get cookies.txt LOCALLY** (Chrome) o **cookies.txt** (Firefox).
2. Abre una **ventana de incógnito** y permite la extensión en ella.
3. Inicia sesión en YouTube.
4. En esa pestaña, ve a `https://www.youtube.com/robots.txt`.
5. Exporta las cookies de `youtube.com` con la extensión.
6. **Cierra la ventana de incógnito** sin cerrar sesión, y no vuelvas a usar esa sesión en el navegador.
7. Pasa el archivo al móvil y, en **Ajustes > Cuenta de YouTube**, pulsa **Importar archivo (.txt o .json)**.

Se admiten el formato Netscape (`cookies.txt`) y el JSON de extensiones como Cookie-Editor o EditThisCookie.

Las cookies se guardan en el almacenamiento privado de la app: ninguna otra app puede leerlas y se borran al desinstalar.

---

## Dónde se guarda todo

| Qué | Dónde | ¿Se borra al desinstalar? |
|---|---|---|
| Canciones descargadas | `Música/Eleuthera/` | No |
| Canciones de una playlist de YouTube | `Música/Eleuthera/<nombre de la playlist>/` | No |
| Playlists, portadas, ajustes y cookies | Almacenamiento privado de la app | Sí |

Los archivos de música son normales: puedes copiarlos, abrirlos con otros reproductores o pasarlos al ordenador.

---

## Solución de problemas

<details>
<summary><b>«La aplicación no se ha instalado» al actualizar</b></summary>

La versión instalada está firmada con otra clave (por ejemplo, la instalaste desde Android Studio). Desinstálala e instala la nueva. Tus MP3 se conservan.
</details>

<details>
<summary><b>«Sign in to confirm you're not a bot»</b></summary>

YouTube está limitando las peticiones. Carga tus [cookies](#cuenta-de-youtube-cookies) o espera un rato antes de seguir.
</details>

<details>
<summary><b>Las descargas fallan de repente</b></summary>

YouTube cambia a menudo y el motor yt-dlp se actualiza solo en cada arranque. Cierra la app del todo (quítala de recientes) y vuelve a abrirla con internet. Si sigue fallando, prueba con cookies.
</details>

<details>
<summary><b>La biblioteca está vacía</b></summary>

Falta el permiso de música: **Ajustes del sistema > Aplicaciones > Eleuthera > Permisos > Música y audio**.
</details>

<details>
<summary><b>No veo el progreso de las descargas</b></summary>

Activa las notificaciones de Eleuthera en los ajustes del sistema.
</details>

<details>
<summary><b>Una canción importada no es la versión correcta</b></summary>

Bórrala desde Biblioteca (menú ⋮ > *Eliminar del dispositivo*), búscala a mano en **Buscar** y añádela a la playlist.
</details>

<details>
<summary><b>Las importaciones grandes se detienen</b></summary>

Android limita el tiempo en segundo plano de algunas apps. Desactiva la optimización de batería para Eleuthera: **Ajustes del sistema > Aplicaciones > Eleuthera > Batería > Sin restricciones**.
</details>

---

## Para desarrolladores

### Tecnología

| Área | Librerías |
|---|---|
| Lenguaje e interfaz | Kotlin, Jetpack Compose, Material 3 |
| Arquitectura | MVVM con capas `domain` / `data` / `ui`, Hilt para dependencias |
| Descarga | [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + FFmpeg + QuickJS) |
| Segundo plano | WorkManager |
| Reproducción | Media3 (ExoPlayer + MediaSessionService) |
| Datos | Room (playlists), DataStore (ajustes), MediaStore (archivos) |
| Otros | Coil, kotlinx-serialization, [Reorderable](https://github.com/Calvin-LL/Reorderable) |

### Estructura

```text
app/src/main/java/com/tuapp/eleuthera/
├── domain/model/     Modelos (Track, Playlist, ThemeConfig…)
├── data/
│   ├── youtube/      Motor yt-dlp
│   ├── download/     Descargas (WorkManager)
│   ├── importer/     Importación de listas y emparejado
│   ├── library/      MediaStore
│   ├── playlist/     Repositorio de playlists
│   ├── db/           Room
│   └── settings/     DataStore, cookies
├── playback/         Servicio y controlador del reproductor
├── di/               Módulos de Hilt
└── ui/               Pantallas Compose (search, library, playlists, player, settings…)
```

> 📘 **Guía del código:** [`docs/estructura.pdf`](docs/estructura.pdf) explica cómo está organizado el proyecto, qué hace cada archivo y dónde tocar para cambiar cada cosa (fuente LaTeX en `docs/estructura.tex`).

### Compilar

El proyecto tiene tres módulos: `app` (Android), `desktop` (Windows, Linux y macOS) y `shared` (lo común: modelos, datos y pantallas, con Kotlin Multiplatform). Gradle usa el JDK 25.

**Android:** abre el proyecto con Android Studio y pulsa ▶ con un móvil conectado (mejor que el emulador).

**Escritorio:**

```bash
./gradlew :desktop:run                              # abrir la app
./gradlew :desktop:packageDistributionForCurrentOS  # instalador del sistema en el que estás
```

El instalador queda en `desktop/build/compose/binaries/main/`. Cada sistema genera solo el suyo (`.msi` en Windows, `.deb` en Linux, `.dmg` en macOS). Si falla al empaquetar con el JDK 25, indica un JDK 21 en la variable `PACKAGING_JAVA_HOME`, que es lo que hace GitHub Actions.

### Flujo de trabajo

- `master` siempre compila y es lo que se publica. No se sube directamente a ella.
- Cada cambio va en su rama (`feature/…`, `fix/…`) y entra por **Pull Request**.
- Antes de abrir el PR, comprueba que compila en local.

### Publicar una versión

Las releases las genera GitHub Actions al subir una etiqueta:

```bash
git checkout master
git pull
git tag v1.2.0
git push origin v1.2.0
```

El workflow `.github/workflows/release.yml` compila el APK firmado y los instaladores de escritorio (cada uno en una máquina de su sistema) y los publica juntos en Releases. También se puede lanzar a mano desde **Actions > Release > Run workflow** para probar los instaladores sin publicar nada: quedan como artefactos de la ejecución. Necesita estos secretos en **Settings > Secrets and variables > Actions**:

| Secreto | Contenido |
|---|---|
| `KEYSTORE_BASE64` | El almacén de claves en Base64 |
| `SIGNING_STORE_PASSWORD` | Contraseña del almacén |
| `SIGNING_KEY_ALIAS` | Alias de la clave |
| `SIGNING_KEY_PASSWORD` | Contraseña de la clave |
| `MUSICBRAINZ_CLIENT_ID` | Cliente de MusicBrainz (en local va en `local.properties`) |
| `MUSICBRAINZ_CLIENT_SECRET` | Secreto de MusicBrainz (en local va en `local.properties`) |

El número de versión se toma de la etiqueta y el `versionCode` del número de ejecución, así que cada release se instala encima de la anterior.

> 🔒 Nunca subas el almacén de claves, sus contraseñas, `local.properties` ni archivos de cookies. El `.gitignore` ya los excluye, pero revisa cada commit.

---

## Créditos

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) y [youtubedl-android](https://github.com/yausername/youtubedl-android), el motor de descarga.
- [FFmpeg](https://ffmpeg.org), la conversión de audio.
- [Exportify](https://github.com/watsonbox/exportify) y [TuneMyMusic](https://www.tunemymusic.com), para exportar playlists.
- Fuentes [Inter](https://rsms.me/inter/), [Montserrat](https://github.com/JulietaUla/Montserrat), [Nunito](https://github.com/googlefonts/nunito) y [Playfair Display](https://github.com/clauseggers/Playfair), bajo licencia SIL Open Font License.

---

## Aviso legal

Eleuthera es un proyecto personal sin ánimo de lucro y no está afiliado a YouTube, Google, Spotify ni Qobuz.

Úsala solo con contenido propio, con licencia libre o para el que tengas permiso. Descargar contenido de YouTube sin autorización puede ir contra sus [Términos de servicio](https://www.youtube.com/t/terms) y contra los derechos de autor de tu país. Cada usuario es responsable del uso que haga de la app.