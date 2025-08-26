plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.notherix.draftkuy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notherix.draftkuy"
        minSdk = 23
        targetSdk = 35
        versionCode = 62
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation(libs.androidx.core.ktx)
    implementation("com.google.code.gson:gson:2.13.1")
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.15.0")
    implementation("com.google.firebase:firebase-database:22.0.0")

    // Firebase BoM dan library
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))  // Updated version
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("com.google.firebase:firebase-database-ktx")  // Add this

    implementation("com.android.billingclient:billing:8.0.0")




    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}