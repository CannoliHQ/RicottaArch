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
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    flavorDimensions += "variant"

    defaultConfig {
        applicationId = "dev.cannoli.ricotta"
        minSdk = 24
        targetSdk = 28
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        missingDimensionStrategy("variant", "normal")

        externalNativeBuild {
            ndkBuild {
                arguments("-j${Runtime.getRuntime().availableProcessors()}")
            }
        }
    }

    productFlavors {
        create("normal") {
            resValue("string", "app_name", "RicottaArch")
            buildConfigField("boolean", "PLAY_STORE_BUILD", "false")
            dimension = "variant"
        }
        create("aarch64") {
            applicationIdSuffix = ".aarch64"
            resValue("string", "app_name", "RicottaArch (AArch64)")
            buildConfigField("boolean", "PLAY_STORE_BUILD", "false")
            dimension = "variant"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        create("ra32") {
            applicationIdSuffix = ".ra32"
            resValue("string", "app_name", "RicottaArch (32-bit)")
            buildConfigField("boolean", "PLAY_STORE_BUILD", "false")
            dimension = "variant"
            ndk { abiFilters += listOf("armeabi-v7a", "x86") }
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
                "$ricottaDir/src"
            )
            jniLibs.srcDir("$phoenixCommonDir/libs")
            jni.setSrcDirs(emptyList<File>())
            res.srcDirs("$phoenixDir/res", "$phoenixCommonDir/res")
        }
        getByName("normal") {
            java.srcDirs("$retroarchDir/pkg/android/play-core-stub")
        }
        getByName("aarch64") {
            java.srcDirs("$retroarchDir/pkg/android/play-core-stub")
            res.srcDirs("$phoenixDir/res", "$phoenixDir/res64")
        }
        getByName("ra32") {
            java.srcDirs("$retroarchDir/pkg/android/play-core-stub")
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
