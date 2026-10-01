import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/*
 * Release signing.
 * Credentials come from environment variables (CI) or from an uncommitted
 * keystore.properties file in the project root (local builds):
 *   ANDROID_KEYSTORE_PATH / storeFile
 *   ANDROID_KEYSTORE_PASSWORD / storePassword
 *   ANDROID_KEY_ALIAS / keyAlias
 *   ANDROID_KEY_PASSWORD / keyPassword
 * Release tasks fail when anything is missing. There is no fallback to debug signing.
 */
val localSigning = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localSigning.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val releaseSigningComplete = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null } && releaseStorePath?.let { rootProject.file(it).isFile || file(it).isFile } == true

android {
    namespace = "com.morningsteps.kids"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.morningsteps.kids"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningComplete) {
                val path = releaseStorePath!!
                storeFile = if (rootProject.file(path).isFile) rootProject.file(path) else file(path)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Step 1 of section 18: a verified, non-minified release comes first.
            // Flip both flags to true (with -PenableR8=true) only after that release passed verification.
            val enableR8 = (project.findProperty("enableR8") as String?)?.toBoolean() ?: false
            isMinifyEnabled = enableR8
            isShrinkResources = enableR8
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Version-update reminders are not build errors; they are reviewed manually.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Fail fast: release packaging must never run without the real release keystore.
val releaseTaskPrefixes = listOf("assembleRelease", "bundleRelease", "packageRelease", "signRelease", "installRelease")
gradle.taskGraph.whenReady {
    val needsRelease = allTasks.any { task ->
        task.project == project && releaseTaskPrefixes.any { task.name.startsWith(it) }
    }
    if (needsRelease && !releaseSigningComplete) {
        throw GradleException(
            "Release signing credentials are missing. Provide ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                "ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD (or keystore.properties). Debug signing is never used for release."
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
