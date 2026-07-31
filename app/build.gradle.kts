import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.kixyu9527.kixyubook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kixyu9527.kixyubook"
        minSdk = 26
        targetSdk = 37
        versionCode = 1075
        versionName = "1.8.0"
    }

    val externalSigningFile = file(
        System.getenv("KIXYU_SIGNING_PROPERTIES")
            ?: "${System.getProperty("user.home")}/.kixyubook/signing.properties",
    )
    val signingProperties = Properties().apply {
        if (externalSigningFile.exists()) externalSigningFile.inputStream().use(::load)
    }
    if (signingProperties.isNotEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-designsystem"))
    implementation(project(":core:core-navigation"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":feature:feature-home"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-reader"))
    implementation(project(":feature:feature-settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)

    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

}

baselineProfile {
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    saveInSrc = true
}
