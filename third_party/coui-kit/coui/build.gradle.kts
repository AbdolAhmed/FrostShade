plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.itos.coui"
    compileSdk = 37
    compileSdkMinor = 1
    defaultConfig { minSdk = 35 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


kotlin {
    compilerOptions {
        optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.runtime)
    api(libs.compose.animation)
    api(libs.compose.foundation)
    implementation(libs.androidx.dynamic.animation)
    implementation(libs.core.ktx)
}
