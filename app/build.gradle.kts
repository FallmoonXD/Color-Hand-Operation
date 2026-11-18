plugins { id("com.android.application") }
android {
 namespace = "dev.edgeflow"
 compileSdk = 37
 defaultConfig {
  applicationId = "dev.edgeflow"
  minSdk = 33
  targetSdk = 35
  versionCode = 29
  versionName = "0.5.0"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 sourceSets.getByName("main") {
  java.directories.add("../lsp/src/main/java")
  resources.directories.add("../lsp/src/main/resources")
  assets.directories.add("../lsp/src/main/assets")
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   // Published APKs use this project's existing development certificate for upgrades.
   signingConfig = signingConfigs.getByName("debug")
  }
 }
}
dependencies {
 implementation(files("libs/hiddenapibypass.jar"))
 implementation("io.github.libxposed:service:102.0.0")
 compileOnly("io.github.libxposed:api:102.0.0")
 compileOnly(project(path=":lsp",configuration="inputStubsElements"))
}
