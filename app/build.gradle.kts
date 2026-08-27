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
    namespace = "com.jcheol.commuteflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jcheol.commuteflow"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GBIS_SERVICE_KEY", publicDataServiceKey.asBuildConfigString())
        buildConfigField(
            "String",
            "SEOUL_TRANSIT_PROXY_URL",
            seoulTransitProxyUrl.asBuildConfigString(),
        )
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
