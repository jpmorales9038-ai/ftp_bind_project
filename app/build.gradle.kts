plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rclonebind.app"
    compileSdk = 36

    // Gradle usa por defecto "~/.android/debug.keystore", que se
    // autogenera con una clave AL AZAR la primera vez que se necesita en
    // cada máquina. En GitHub Actions eso significa una clave nueva en
    // CADA build, así que cada APK queda firmado distinto y Android
    // rechaza instalar la actualización sobre la anterior a menos que se
    // desinstale primero ("no me deja instalar sobre la anterior sin
    // desinstalar" = INSTALL_FAILED_UPDATE_INCOMPATIBLE / conflicto de
    // firma). Usando este keystore versionado en el repo, todos los
    // builds (locales o en CI) firman siempre con la misma clave.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.rclonebind.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.5.0"
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // Material 3 Expressive vive en la línea 1.5.0-alpha (la 1.4.0 estable no lo trae).
    // alpha23: última que no exige compileSdk 37 / Compose 1.12 (alpha24+ sí).
    implementation("androidx.compose.material3:material3:1.5.0-alpha23")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // libsu: ejecutar comandos root de forma segura
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")
}
