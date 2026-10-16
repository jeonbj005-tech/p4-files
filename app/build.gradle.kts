import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val oauthProperties = Properties().apply {
    val file = rootProject.file("oauth.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val updateProperties = Properties().apply {
    val file = rootProject.file("update.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun quotedBuildValue(name: String): String =
    "\"" + (oauthProperties.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")) + "\""

android {
    namespace = "com.polestar4.drivefilemanager"; compileSdk = 35
    defaultConfig { applicationId = "com.polestar4.drivefilemanager"; minSdk = 26; targetSdk = 35; versionCode = 47; versionName = "1.37"
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"\"")
        buildConfigField("String", "OAUTH_CLIENT_SECRET", "\"\"")
        buildConfigField("String", "TV_OAUTH_CLIENT_ID", quotedBuildValue("clientId"))
        buildConfigField("String", "TV_OAUTH_CLIENT_SECRET", quotedBuildValue("clientSecret"))
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"" + (updateProperties.getProperty("manifestUrl", "").replace("\\", "\\\\").replace("\"", "\\\"")) + "\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
