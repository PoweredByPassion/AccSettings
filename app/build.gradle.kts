plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33
    namespace = "crazyboyfeng.accSettings"
    defaultConfig {
        applicationId = "crazyboyfeng.accSettings"
        minSdk = 14
        targetSdk = 36
        versionCode = 202512070
        versionName = "2025.12.7"
//        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        resValue("string", "version_name", versionName!!)
    }
//    buildFeatures { viewBinding = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
//    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.work:work-runtime:2.7.1")
    implementation("androidx.activity:activity-ktx:1.6.0")
    implementation("com.github.topjohnwu.libsu:core:3.2.1")
    val axpeVersion = "0.9.0"
    implementation("com.github.CrazyBoyFeng.AndroidXPreferenceExtensions:edittext:$axpeVersion")
    implementation("com.github.CrazyBoyFeng.AndroidXPreferenceExtensions:numberpicker:$axpeVersion")
//    testImplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test.ext:junit:1.1.3")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}