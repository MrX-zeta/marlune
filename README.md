# 🌙 Marlune

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)
![Media3](https://img.shields.io/badge/Media3_ExoPlayer-EF6C00?style=for-the-badge&logo=android&logoColor=white)
![Room](https://img.shields.io/badge/Room-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Glance](https://img.shields.io/badge/Jetpack_Glance-Widget-34A853?style=for-the-badge)

## 📌 Descripción

**Marlune** es un reproductor de música **local** para Android, nativo y sin cuentas: tu biblioteca es la fuente de verdad y todo funciona offline. La única conexión que hace es opcional — descargar **letras sincronizadas** de [LRCLIB](https://lrclib.net), que luego cachea en el dispositivo.

Sus señas de identidad: una interfaz Compose con **acento dinámico extraído de la carátula** en reproducción, una transición de **elemento compartido** entre el mini-player y el reproductor a pantalla completa, un **widget Glance** reactivo con tres layouts según tamaño, y una **persistencia de sesión** que sobrevive a la muerte del proceso: cierras la app (o reinicias el teléfono) y la cola, la pista y la posición siguen ahí — incluso el widget puede reanudar la reproducción en frío sin abrir la app.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Media3 (ExoPlayer + MediaSession) · Room · DataStore · Jetpack Glance · Coil · Palette · Coroutines/Flow · KSP.

---

## Problema que resuelve

- **Los reproductores locales suelen estar abandonados o recargados:** interfaces de otra década o apps con streaming, anuncios y cuentas para algo que vive en tu almacenamiento.
- **La reproducción en segundo plano es frágil:** apps que pierden la cola al matar el proceso o cuyo widget se congela cuando la UI muere.
- **Los widgets de música rotos son la norma:** controles que abren la app en vez de actuar, estados desactualizados, crashes en builds de release por ofuscación.

Marlune ataca los tres: reproducción delegada a un servicio Media3 independiente de la UI, sesión persistida y restaurable desde cero, y un widget alimentado **por eventos del servicio** (no por polling ni por la UI) que funciona igual con la app cerrada.

---

## Responsabilidades principales

- **Reproducción local** (Media3/ExoPlayer en un `MediaSessionService`): cola, shuffle, notificación multimedia, audio focus y controles de auriculares/sistema.
- **Biblioteca desde MediaStore:** canciones, álbumes, artistas y "añadidas recientemente", con búsqueda y detalle por entidad.
- **Listas de reproducción** (Room): creación, añadir/quitar canciones y **reordenado por arrastre**.
- **Favoritos e historial** (Room): "Me gusta" con su pantalla propia, historial de escucha deduplicado y un **Mix de biblioteca** sesgado a lo menos escuchado.
- **Letras sincronizadas** (LRCLIB): búsqueda con verificación de identidad de la pista, parser LRC propio y caché local descargada (gestionable desde Ajustes).
- **Persistencia de sesión** (DataStore): cola + índice + posición; restauración en pausa al arrancar y `onPlaybackResumption` para reanudar desde el widget/auriculares con el proceso muerto.
- **Widget de pantalla de inicio** (Glance): tres layouts responsivos (tira, horizontal, vertical), carátula, acento dinámico, controles que actúan in situ y toque en carátula/texto que abre Now Playing con su transición.
- **Reproductor a pantalla completa:** carátula que "viaja" desde el mini-player (shared element), swipe para cambiar de pista, cola en hoja modal, temporizador de apagado y "me gusta" al vuelo.

---

## Flujo general

```
  UI (Compose + ViewModels) ──── MediaController ────► MarluneMediaService
                                                        (Media3 · proceso propio de la UI,
            ▲                                            vida independiente de las Activity)
            │ StateFlow                                          │
            │                                                    ├── eventos (pista/play/shuffle/like)
  Repositorios ◄── Room (favoritos, listas, historial)           ▼
            ▲       DataStore (sesión, ajustes)          WidgetPlaybackBus ──► Widget Glance
            │                                                    ▲
  MediaStore (biblioteca real del dispositivo)                   │ frío: sesión guardada
                                                                 └── (pinta y REANUDA sin abrir la app)

  LRCLIB (HTTPS, opcional) ──► parser LRC ──► caché local de letras
```

- El widget **nunca** lee de la UI: el servicio publica cada cambio en un bus in-process y Glance repinta solo con eventos (cero refrescos periódicos).
- Con el proceso muerto, el widget se pinta desde la **sesión persistida** y sus controles despiertan el servicio vía intents de botón multimedia (arranque foreground legal desde un widget).

---

## Estructura interna

**Capas (paquetes bajo `com.luis.marlune`)**

- `data/` — MediaStore (escáner de biblioteca), Room (entidades/DAOs), DataStore (sesión y ajustes), cliente y caché de letras, repositorios.
- `domain/` — modelos (`Song`, `PlaylistCover`, …) sin dependencias de Android.
- `playback/` — `MarluneMediaService` (Media3) y el repositorio de reproducción que habla con el `MediaController`.
- `di/` — **inyección manual** vía `AppContainer` (composición explícita, sin framework de DI).
- `ui/` — pantallas Compose por feature (`home`, `library`, `player`, `search`, `detail`, `settings`, `onboarding`) + `widget/` (Glance) + tema con acento dinámico.

**Persistencia**

- **Room:** `FavoriteEntity`, `PlaylistEntity` (+ orden de canciones), `PlayHistoryEntity` — solo referencias a `_ID` de MediaStore, nunca duplicados de metadatos.
- **DataStore:** sesión guardada (ids de la cola, índice, posición, shuffle, metadatos de la pista actual para pintar en frío) y preferencias.
- **MediaStore:** fuente de verdad de la biblioteca; la app no copia ni reindexa archivos.

---

## Decisiones técnicas

- **`MediaSessionService` + `onPlaybackResumption`:** la reproducción no depende de la UI, y la reanudación en frío (widget, auriculares) usa el camino canónico de Media3 en vez de arranques de servicio bloqueados por el sistema.
- **Widget por eventos, no por polling:** el servicio empuja estado a un `WidgetPlaybackBus`; sin `WorkManager`, sin alarmas, sin refrescos en reposo. El acento del widget se extrae de la carátula en el servicio, no en Glance.
- **Blindaje MIUI/HyperOS real:** círculo del play como drawable oval (los `Box` con `cornerRadius` se rasterizan deformados), tamaños fijos sin `weight` en filas de controles, y arranques foreground vía intent de botón multimedia. Probado en dispositivo físico Xiaomi.
- **Acento dinámico con Palette** cacheado por carátula y aplicado a toda la UI (tema, mini-player, widget) con fallback de marca para carátulas monocromas.
- **DI manual (`AppContainer`)** en lugar de Hilt: el grafo es pequeño y estable; composición explícita, cero magia y build más simple (solo KSP para Room).
- **Letras sin dependencias de red pesadas:** `HttpURLConnection` + `org.json`, verificación de que el resultado corresponde a la pista (título/artista/duración) y caché local para no repetir peticiones.
- **Release endurecido:** R8 con reglas mínimas *justificadas* (keep de los `ActionCallback` del widget, que Glance instancia por reflexión — el crash clásico de widget en release) y firma fuera del repo (`keystore.properties` git-ignored).
- **Accesibilidad y movimiento:** respeta `reduced motion` del sistema en las transiciones y mantiene áreas táctiles de 48 dp en todos los controles del widget.

---

## Desarrollo local

**Requisitos**

- Android Studio reciente · JDK 17 · Android SDK 37 · dispositivo/emulador **API 28+** con música local.
- Sin secretos ni servicios que configurar: la build **debug** funciona tras sincronizar Gradle.

**Ejecutar**

```bash
git clone https://github.com/MrX-zeta/Marlune.git
cd Marlune
./gradlew installDebug   # o Run desde Android Studio
```

**Release firmado** (la firma vive fuera del repo):

1. Genera un keystore con `keytool` y guárdalo fuera del proyecto.
2. Crea `keystore.properties` en la raíz (git-ignored) con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Sin él, debug compila normal y las tareas de release abortan con un mensaje claro.
3. `./gradlew bundleRelease` (AAB para Play) o `./gradlew assembleRelease` (APK) → `app/build/outputs/`.

---

## Pruebas

Sin suite automatizada aún; la validación es funcional y **en dispositivo físico**, incluyendo la matriz difícil: build de release con R8, widget en frío tras reinicio, muerte de proceso con restauración de sesión, y los quirks de MIUI. El diseño deja la puerta abierta: dominio puro sin Android, repositorios tras interfaces y estado observable en ViewModels.

---

## Valor del proyecto

Marlune es un **producto autónomo** cuyo mérito técnico está en los problemas que un reproductor real obliga a resolver bien:

- **Ciclo de vida serio:** servicio multimedia independiente de la UI, sesión que sobrevive a la muerte del proceso y reanudación en frío desde el widget.
- **Integración profunda con el sistema:** MediaStore, MediaSession (notificación, auriculares, resumption), widget Glance por eventos y comportamiento correcto bajo launchers agresivos (MIUI).
- **Pulido de producto:** acento dinámico, transiciones de elemento compartido, reduced motion, y un pipeline de release completo (firma segura + R8 con keeps mínimos y justificados).

---

*Desarrollado por [Brian Luis Ruiz Pérez (MrX-zeta)](https://github.com/MrX-zeta).*
