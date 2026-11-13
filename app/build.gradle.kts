plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.idrisokys.dosyalarim"
    compileSdk = 34
    defaultConfig { applicationId = "io.github.idrisokys.dosyalarim"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.12.0"); implementation("androidx.viewpager:viewpager:1.0.0") }
