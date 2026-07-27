plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.kixyu9527.kixyubook.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
