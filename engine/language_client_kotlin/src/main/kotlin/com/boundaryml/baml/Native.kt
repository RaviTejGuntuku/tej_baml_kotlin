package com.boundaryml.baml

/**
 * Singleton holder for the loaded native library.
 * Call [BamlFfi.load] to initialize, or access [BamlFfi.instance] after loading.
 *
 * Automatically selects JNA (desktop JVM) or JNI (Android) based on runtime detection.
 */
object BamlFfi {
    @Volatile
    var instance: NativeBamlLib? = null
        private set

    /**
     * Load the bridge_cffi dynamic library.
     *
     * On desktop JVM:
     *   Resolution order: explicit [path] → BAML_LIBRARY_PATH env → bundled JAR resource → system path.
     *
     * On Android:
     *   Loads via System.loadLibrary from the APK's jniLibs directory.
     *   The [path] parameter is ignored on Android.
     */
    fun load(path: String? = null): NativeBamlLib {
        val lib = if (isAndroid()) {
            JniBamlLib()
        } else {
            JnaBamlLib.create(path)
        }
        instance = lib
        return lib
    }

    /**
     * Detect whether we're running on Android (Dalvik/ART VM).
     */
    private fun isAndroid(): Boolean {
        return try {
            Class.forName("android.os.Build")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
