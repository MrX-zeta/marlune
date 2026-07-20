# ============================ Reglas R8 de Marlune ============================
# Filosofía: SOLO lo que el código real necesita. Media3, Room, Coil, DataStore y
# Glance publican sus reglas "consumer" dentro de cada AAR y R8 las aplica
# automáticamente — no se duplican aquí. El código propio no usa Gson/Moshi, ni
# @Parcelize, ni kotlinx.serialization, ni Class.forName; Room va sin
# TypeConverters; las letras usan HttpURLConnection + org.json (framework). Los
# receivers/servicios/Activity del manifest los conserva AAPT por defecto.

# --- Acciones del widget (Glance ActionCallback) ---
# Glance instancia estas clases POR REFLEXIÓN: actionRunCallback<T>() guarda el
# nombre de la clase en los extras del PendingIntent y su receiver hace
# Class.forName(nombre).newInstance(). Sin keep, R8 puede podar el constructor
# sin argumentos (nunca se invoca directamente) y, peor, los PendingIntents que
# el launcher conserva entre ACTUALIZACIONES guardarían un nombre ofuscado de la
# versión anterior que ya no existe en la nueva. Mantener clase+nombre+ctor
# cierra ambos: son 5 clases mínimas, coste despreciable.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# --- Crashes legibles en producción ---
# Conserva números de línea (y anonimiza el nombre de archivo) para que los
# stacktraces de Play Console apunten a líneas reales pese a la ofuscación.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
