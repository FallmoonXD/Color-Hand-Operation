plugins { id("com.android.application") }
android {
 namespace = "dev.edgeflow"
 compileSdk = 35
 defaultConfig {
  applicationId = "dev.edgeflow"
  minSdk = 33
  targetSdk = 35
  versionCode = 20
  versionName = "0.3.17"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   // Published APKs use this project's existing development certificate for upgrades.
   signingConfig = signingConfigs.getByName("debug")
  }
 }
}
dependencies { implementation(files("libs/hiddenapibypass.jar")) }
