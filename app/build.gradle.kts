plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.project1.psira"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.project1.psira"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = java.util.Properties()
            val keyFile = rootProject.file("keystore.properties")
            if (keyFile.exists()) props.load(keyFile.inputStream())
            storeFile     = props.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = props.getProperty("storePassword") ?: ""
            keyAlias      = props.getProperty("keyAlias") ?: ""
            keyPassword   = props.getProperty("keyPassword") ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.firebase:firebase-database-ktx:20.3.0")
    implementation("com.google.firebase:firebase-auth:22.3.1")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("io.getstream:stream-webrtc-android:1.1.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}