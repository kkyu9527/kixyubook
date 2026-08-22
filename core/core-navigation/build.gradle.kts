plugins {
    alias(libs.plugins.android.library)
    id("kotlin-parcelize")
}

android {
    namespace = "com.kixyu9527.kixyubook.core.navigation"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)
}
