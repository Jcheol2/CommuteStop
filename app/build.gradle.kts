import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val publicDataServiceKey = providers.gradleProperty("PUBLIC_DATA_SERVICE_KEY").orNull
    ?.trim()
    .orEmpty()
    .ifBlank { localProperties.getProperty("PUBLIC_DATA_SERVICE_KEY").orEmpty().trim() }

fun configuredValue(name: String): String = providers.gradleProperty(name).orNull
    ?.trim()
    .orEmpty()
    .ifBlank { localProperties.getProperty(name).orEmpty().trim() }

val seoulTransitProxyUrl = configuredValue("SEOUL_TRANSIT_PROXY_URL")

fun privateValue(name: String): String = providers.environmentVariable(name).orNull
    ?: providers.gradleProperty(name).orNull
    ?: localProperties.getProperty(name).orEmpty()

val releaseStoreFile = privateValue("COMMUTEFLOW_RELEASE_STORE_FILE").trim()
val releaseStorePassword = privateValue("COMMUTEFLOW_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = privateValue("COMMUTEFLOW_RELEASE_KEY_ALIAS").trim()
val releaseKeyPassword = privateValue("COMMUTEFLOW_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
check(releaseSigningValues.all(String::isBlank) || releaseSigningValues.none(String::isBlank)) {
    "Release signing requires COMMUTEFLOW_RELEASE_STORE_FILE, " +
        "COMMUTEFLOW_RELEASE_STORE_PASSWORD, COMMUTEFLOW_RELEASE_KEY_ALIAS, " +
        "and COMMUTEFLOW_RELEASE_KEY_PASSWORD together."
}
val hasReleaseSigningConfig = releaseSigningValues.none(String::isBlank)

fun String.asBuildConfigString(): String = buildString {
    append('"')
    this@asBuildConfigString.forEach { character ->
        append(
            when (character) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\n' -> "\\n"
                '\r' -> "\\r"
                else -> character
            },
        )
    }
    append('"')
}

android {
    val releaseSigningConfig = if (hasReleaseSigningConfig) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    namespace = "com.jcheol.commuteflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jcheol.commuteflow"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GBIS_SERVICE_KEY", publicDataServiceKey.asBuildConfigString())
        buildConfigField(
            "String",
            "SEOUL_TRANSIT_PROXY_URL",
            seoulTransitProxyUrl.asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            releaseSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
