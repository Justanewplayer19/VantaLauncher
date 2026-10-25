// Throwaway icon pack used to exercise the launcher's pack parser on the emulator.
// Not part of the launcher; build and install it separately with :testpack:installDebug.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.maria.testpack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maria.testpack"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
