plugins { id("com.android.application") }
android {
 namespace = "dev.edgeflow"
 compileSdk = 35
 defaultConfig {
  applicationId = "dev.edgeflow"
  minSdk = 33
  targetSdk = 35
  versionCode = 19
  versionName = "0.3.16"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { getByName("release") { isMinifyEnabled = false } }
}
dependencies { implementation(files("libs/hiddenapibypass.jar")) }
