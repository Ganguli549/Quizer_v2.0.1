plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.test"
    compileSdk = 35
}
dependencies {
    implementation("androidx.compose.material3:material3:1.4.0-alpha07")
}
