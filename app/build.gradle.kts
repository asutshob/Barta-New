import java.util.Base64
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.barta.mrypqk"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystoreFile = rootProject.file("debug.keystore")
  if (!keystoreFile.exists()) {
    val base64File = rootProject.file("debug.keystore.base64")
    if (base64File.exists()) {
      try {
        val base64Content = base64File.readText().trim().replace("\n", "").replace("\r", "")
        val decodedBytes = Base64.getDecoder().decode(base64Content)
        keystoreFile.writeBytes(decodedBytes)
        println("Successfully decoded debug.keystore from base64!")
      } catch (e: Exception) {
        println("Error decoding debug.keystore: ${e.message}")
      }
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
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
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val rootDirRef = rootDir
tasks.register<Copy>("copyApkToOutputs") {
  dependsOn("assembleDebug")
  from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  into(file("${rootDirRef}/.build-outputs"))
  outputs.upToDateWhen { false }
}

tasks.register<Copy>("copyApkToDownload") {
  dependsOn("assembleDebug")
  from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  into(file("${rootDirRef}/APK_DOWNLOAD"))
  rename("app-debug.apk", "Barta.apk")
  outputs.upToDateWhen { false }
}

tasks.register<Copy>("copyApkToDownloadDebug") {
  dependsOn("assembleDebug")
  from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  into(file("${rootDirRef}/APK_DOWNLOAD"))
  outputs.upToDateWhen { false }
}

tasks.register("copyApk") {
  dependsOn("copyApkToOutputs", "copyApkToDownload", "copyApkToDownloadDebug")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
  finalizedBy("copyApk")
}

tasks.register("verifyApk") {
  dependsOn("copyApk")
  notCompatibleWithConfigurationCache("Accesses project files directly")
  doLast {
    val outputsApk = File(rootDirRef, ".build-outputs/app-debug.apk")
    val downloadApk = File(rootDirRef, "APK_DOWNLOAD/Barta.apk")
    val debugKeystore = File(rootDirRef, "debug.keystore")
    println("Outputs APK size: ${outputsApk.length()} bytes")
    println("Download APK size: ${downloadApk.length()} bytes")
    println("Debug Keystore path: ${debugKeystore.absolutePath}")
    println("Debug Keystore exists: ${debugKeystore.exists()}")
    if (debugKeystore.exists()) {
      println("Debug Keystore size: ${debugKeystore.length()} bytes")
    }
    if (!outputsApk.exists() || outputsApk.length() < 1024 * 1024) {
      throw GradleException("Verification failed: .build-outputs/app-debug.apk is invalid or too small!")
    }
    if (!downloadApk.exists() || downloadApk.length() < 1024 * 1024) {
      throw GradleException("Verification failed: APK_DOWNLOAD/Barta.apk is invalid or too small!")
    }
    println("Verification passed successfully! Real installable APK generated.")
  }
}


