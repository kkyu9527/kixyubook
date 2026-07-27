plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.kixyu9527.kixyubook.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
