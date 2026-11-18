import java.util.Properties

plugins { id("com.android.application") }

// This module contains Java only; AGP's built-in Kotlin plugin otherwise adds
// an unused runtime library to the independent diagnostic APK.
configurations.matching { it.name=="debugRuntimeClasspath" || it.name=="releaseRuntimeClasspath" }
    .configureEach { exclude(group="org.jetbrains.kotlin",module="kotlin-stdlib") }

abstract class SyncGestureCore : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun copySources() {
        sources.files.forEach {
            val destination=outputDirectory.dir(if(it.parentFile.name=="replica") "replica" else "dev/edgeflow").get().asFile
            destination.mkdirs();it.copyTo(destination.resolve(it.name), overwrite=true)
        }
    }
}
val syncGestureCore = tasks.register<SyncGestureCore>("syncGestureCore") {
    sources.from(rootProject.files("app/src/main/java/dev/edgeflow/GestureRecognizer.java", "app/src/main/java/dev/edgeflow/GestureAngles.java", "app/src/main/java/dev/edgeflow/GestureHandleConfig.java"))
    sources.from(rootProject.files(*arrayOf("GestureWaveRenderer.java","GestureAppearance.java","GestureHaptics.java","GestureHapticPlayer.java").map { "app/src/main/java/dev/edgeflow/$it" }.toTypedArray()))
    sources.from(rootProject.files("app/src/main/java/replica/ColorSpringTrack.java","app/src/main/java/replica/ColorControlPhysics.java"))
    sources.from(rootProject.file("app/src/main/java/dev/edgeflow/ToolHostContract.java"))
    sources.from(rootProject.file("app/src/main/java/dev/edgeflow/BackendChoice.java"))
    sources.from(rootProject.file("app/src/main/java/dev/edgeflow/BackendActions.java"))
    outputDirectory.set(layout.buildDirectory.dir("generated/gesture-core"))
}
abstract class SyncFeedbackResources : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun copyResources(){
        sources.files.forEach {
            val directory=outputDirectory.dir(it.parentFile.name).get().asFile
            directory.mkdirs();it.copyTo(directory.resolve(it.name),overwrite=true)
        }
    }
}
val syncFeedbackResources=tasks.register<SyncFeedbackResources>("syncFeedbackResources"){
    sources.from(rootProject.fileTree("app/src/main/res"){include("drawable/oho_gesture_arrow.xml","values*/wave.xml")})
    outputDirectory.set(layout.buildDirectory.dir("generated/feedback-res"))
}

android {
    namespace = "dev.edgeflow.lsp"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.edgeflow.lsp.pilot"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(syncGestureCore) { it.outputDirectory }
    variant.sources.res?.addGeneratedSourceDirectory(syncFeedbackResources) { it.outputDirectory }
}

// Compile against the hidden receiver contract, but resolve these classes from
// Android's boot class loader at runtime. The stubs must never enter the APK.
val localSdk = Properties().apply {
    val path = rootProject.file("local.properties")
    if (path.isFile) path.inputStream().use { load(it) }
}.getProperty("sdk.dir")
val sdk = file(localSdk ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: rootProject.file(".toolchains/android-sdk").path)
val compileInputStubs = tasks.register<JavaCompile>("compileInputStubs") {
    source = fileTree("src/stubs/java") { include("**/*.java") }
    classpath = files(sdk.resolve("platforms/android-35/android.jar"))
    destinationDirectory.set(layout.buildDirectory.dir("input-stubs/classes"))
    options.release.set(17)
}
val inputStubs = tasks.register<Jar>("inputStubs") {
    dependsOn(compileInputStubs)
    from(compileInputStubs.flatMap { it.destinationDirectory })
    archiveFileName.set("input-stubs.jar")
    destinationDirectory.set(layout.buildDirectory.dir("input-stubs"))
}

configurations.create("inputStubsElements") {
    isCanBeConsumed=true;isCanBeResolved=false
    outgoing.artifact(inputStubs.flatMap { it.archiveFile }) { builtBy(inputStubs) }
}

dependencies {
    implementation("io.github.libxposed:service:102.0.0")
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly(files(inputStubs.flatMap { it.archiveFile }).builtBy(inputStubs))
}
