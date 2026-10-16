plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * NOT part of the deliverable. This is a deliberately *benign* test app that declares a
 * hostile-looking permission profile ("Torch Pro" holding SMS / contacts / camera / mic /
 * location / overlay / accessibility / device-admin declarations) so you can verify the
 * scanner's scoring, special-access detection and AI purpose check on a real device
 * without ever handling real malware.
 *
 * Build:  ./gradlew :testkit:assembleDebug
 * Install: adb install app/build/outputs/apk/debug/testkit-debug.apk
 */
android {
    namespace = "com.torch.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.torch.pro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Intentionally dependency-free: a tiny APK that only exists to carry permissions.
}
