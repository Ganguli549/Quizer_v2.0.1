plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.bmwquiz.xyza"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "3.0.1"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release.keystore"
      val ksFile = file(keystorePath)
      if (ksFile.exists()) {
        storeFile = ksFile
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else {
        val debugKs = file("${rootDir}/debug.keystore")
        storeFile = if (debugKs.exists()) debugKs else file("${rootDir}/release.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    jniLibs {
      keepDebugSymbols += "**/libandroidx.graphics.path.so"
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
    ignoreWarnings = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation("com.github.CanHub:Android-Image-Cropper:4.5.0")
  implementation("io.noties.markwon:core:4.6.2")
  implementation("io.noties.markwon:html:4.6.2")
  implementation("io.noties.markwon:ext-latex:4.6.2")
  implementation("io.noties.markwon:image-coil:4.6.2")
  implementation("io.noties.markwon:inline-parser:4.6.2")
  implementation("io.noties.markwon:ext-strikethrough:4.6.2")
  implementation("io.noties.markwon:ext-tables:4.6.2")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation("androidx.compose.material3:material3:1.4.0-alpha08")
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation("io.coil-kt:coil-gif:2.7.0")
  implementation("io.coil-kt:coil-svg:2.7.0")
  implementation("io.coil-kt:coil-video:2.7.0")
  implementation(libs.gson)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.12.0")
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  "ksp"(libs.androidx.room.compiler)
}
