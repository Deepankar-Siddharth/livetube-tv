plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

fun String.asBuildConfigString(): String {
    require(!contains('\n') && !contains('\r')) { "BuildConfig values must not contain newlines" }
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

fun semanticVersionCode(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(version)
        ?: error("app.version must be a stable major.minor.patch version")
    val major = match.groupValues[1].toLong()
    val minor = match.groupValues[2].toLong()
    val patch = match.groupValues[3].toLong()
    val code = major * 1_000_000L + minor * 1_000L + patch
    require(code in 1..2_100_000_000L) { "app.version produces an invalid Android versionCode" }
    return code.toInt()
}

val appVersion = providers.gradleProperty("app.version").getOrElse("1.0.0")
val newPipeVersion = providers.gradleProperty("newpipe.version").getOrElse("v0.26.5")
val githubOwner = providers.gradleProperty("github.owner").getOrElse("")
val githubRepository = providers.gradleProperty("github.repository").getOrElse("")
val githubBranch = providers.gradleProperty("github.branch").getOrElse("main")

android {
    namespace = "com.livetube.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.livetube.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = semanticVersionCode(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "GITHUB_OWNER", githubOwner.asBuildConfigString())
        buildConfigField("String", "GITHUB_REPOSITORY", githubRepository.asBuildConfigString())
        buildConfigField("String", "GITHUB_BRANCH", githubBranch.asBuildConfigString())
        buildConfigField("String", "NEWPIPE_EXTRACTOR_VERSION", newPipeVersion.asBuildConfigString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val storePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
            val storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            val keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            val keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            if (!storePath.isNullOrBlank() && file(storePath).exists() &&
                !storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()
            ) {
                signingConfig = signingConfigs.create("releaseFromEnvironment") {
                    storeFile = file(storePath)
                    this.storePassword = storePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                    enableV4Signing = true
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)

    // NewPipeExtractor is published through JitPack. The version is controlled by
    // gradle.properties so the dependency-update workflow can update it atomically.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$newPipeVersion")
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}
