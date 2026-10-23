plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.anibix.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.anibix.anime"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
    // Tanda tangan debug TETAP dari keystore repo: semua build CI memakai
    // kunci yang sama, jadi APK hasil build bisa menimpa pemasangan lama.
    // Ini kunci DEBUG (bukan kunci rilis) — jangan dipakai untuk publikasi.
    signingConfigs {
        // Timpa ISI konfigurasi "debug" bawaan: build type debug sudah
        // menunjuk ke sini, jadi mengganti field-nya pasti mengganti kunci.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // DI (Hilt)
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Local database (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Settings storage (DataStore)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking (Retrofit + OkHttp + kotlinx.serialization)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Player (Media3)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Torrent engine (libtorrent wrapper) + HTTP server lokal untuk streaming
    implementation("com.frostwire:jlibtorrent:1.2.0.18")
    implementation("com.frostwire:jlibtorrent-android-arm64:1.2.0.18")
    implementation("com.frostwire:jlibtorrent-android-arm:1.2.0.18")
    implementation("com.frostwire:jlibtorrent-android-x86_64:1.2.0.18")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}
