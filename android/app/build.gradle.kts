plugins { id("com.android.application") }

android {
    namespace = "com.takeabreak.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.takeabreak.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
