plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rclonebind.app"
    compileSdk = 34

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
        versionCode = 5
        versionName = "0.1.4"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // libsu: ejecutar comandos root de forma segura
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")
}
