import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.budgetmeals.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.budgetmeals.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("uiTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-ui-test"
            matchingFallbacks += "debug"
        }
    }

    // Device tests use their own application and storage, separate from the user's installed app.
    testBuildType = "uiTest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyDevelopmentSigningKey = tasks.register("verifyDevelopmentSigningKey") {
    group = "verification"
    description = "Checks the shared development signing identity before packaging an APK."

    val keyFile = rootProject.file(".signing/debug.keystore")
    val expectedSha256 = "b6d4a214235923280758ca66a5ffe2604467a2cb2bf2f02f54566aa3b0082d28"
    inputs.files(keyFile).withPropertyName("developmentKeystore")
    inputs.property("expectedCertificateSha256", expectedSha256)

    doLast {
        if (!keyFile.isFile) {
            throw GradleException(
                "Missing .signing/debug.keystore. Copy the shared BudgetMeals development key " +
                    "from your other laptop or private backup; see README.md. Do not generate a new key.",
            )
        }
        val keyStore = try {
            KeyStore.getInstance(keyFile, "android".toCharArray())
        } catch (error: Exception) {
            throw GradleException("Cannot read the shared development key. See README.md.", error)
        }
        val certificate = keyStore.getCertificate("androiddebugkey")
            ?: throw GradleException("The shared development key is missing alias androiddebugkey.")
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actualSha256 != expectedSha256) {
            throw GradleException(
                "Wrong BudgetMeals development signing key. Copy the original .signing/debug.keystore " +
                    "from your other laptop or private backup; see README.md. " +
                    "Changing the key would prevent updates to the installed app.",
            )
        }
    }
}

tasks.matching {
    it.name in setOf("validateSigningDebug", "validateSigningUiTest", "validateSigningUiTestAndroidTest", "validateSigningRelease")
}.configureEach {
    dependsOn(verifyDevelopmentSigningKey)
}

composeCompiler {
    includeSourceInformation = false
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    add("uiTestImplementation", "androidx.compose.ui:ui-test-manifest")
}
