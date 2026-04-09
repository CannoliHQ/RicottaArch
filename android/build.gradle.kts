plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Paths relative to this build file (android/)
val retroarchDir = file("../retroarch")
val ricottaDir = file("../ricotta")
val phoenixDir = file("$retroarchDir/pkg/android/phoenix")
val phoenixCommonDir = file("$retroarchDir/pkg/android/phoenix-common")

android {
    namespace = "com.retroarch"
    compileSdk = 36
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "dev.cannoli.ricotta"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.currentTimeMillis() / 1000).toInt()

        resValue("string", "app_name", "RicottaArch")
        buildConfigField("boolean", "PLAY_STORE_BUILD", "false")

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        externalNativeBuild {
            ndkBuild {
                arguments("-j${Runtime.getRuntime().availableProcessors()}")
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("$phoenixDir/AndroidManifest.xml")
            assets.srcDirs("$phoenixDir/assets")
            java.srcDirs(
                "$phoenixDir/src",
                "$phoenixCommonDir/src",
                "$retroarchDir/libretro-common/vfs/saf/src",
                "$retroarchDir/pkg/android/play-core-stub",
                "$ricottaDir/src"
            )
            jniLibs.srcDir("$phoenixCommonDir/libs")
            jni.setSrcDirs(emptyList<File>())
            res.srcDirs("$phoenixDir/res", "$phoenixCommonDir/res")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("$phoenixCommonDir/jni/Android.mk")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
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

// Exclude RetroArch's RetroActivityFuture — we replace it with our own
tasks.withType<JavaCompile>().configureEach {
    exclude("com/retroarch/browser/retroactivity/RetroActivityFuture.java")
}

dependencies {
    implementation(project(":cannoli-igm"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
}
