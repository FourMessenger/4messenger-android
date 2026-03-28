package io.github.fourmessenger;

/**
 * JNI bridge to the Rust native library.
 * Provides native performance utilities for the 4 Messenger app.
 */
public class NativeLib {

    static {
        try {
            System.loadLibrary("fourmessenger");
        } catch (UnsatisfiedLinkError e) {
            // Native library not available, use Java fallbacks
        }
    }

    /**
     * Get the native library version string.
     */
    public static native String getVersion();

    /**
     * Initialize native components.
     * @return true if initialization succeeded
     */
    public static native boolean initialize();

    /**
     * Compute a fast hash of the given string using Rust's native implementation.
     * @param input the string to hash
     * @return 64-bit hash value
     */
    public static native long computeHash(String input);

    /**
     * Safe version of getVersion() with Java fallback.
     */
    public static String getVersionSafe() {
        try {
            return getVersion();
        } catch (UnsatisfiedLinkError e) {
            return "1.0.0";
        }
    }
}
