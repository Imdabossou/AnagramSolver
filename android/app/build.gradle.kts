import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing comes from a git-ignored keystore.properties. Without one,
// release builds use the debug key so they can still be installed for testing.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

// The app is the website itself: these files are copied from the repo root into
// the APK's assets (under site/) at build time, so there is only one copy of the app.
abstract class CopyWebFiles : DefaultTask() {
    @get:Internal abstract val webDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    val files: FileTree
        get() = webDir.asFileTree.matching {
            include("index.html", "style.css", "app.js", "theme.js", "icon.svg", "words/*.txt")
        }

    @TaskAction fun copy() {
        val tree = files
        fs.sync { from(tree); into(outputDir.dir("site")) }
    }
}
val copyWebFiles = tasks.register<CopyWebFiles>("copyWebFiles") {
    webDir.set(rootProject.layout.projectDirectory.dir(".."))
}

android {
    namespace = "uk.jimtec.anagramsolver"
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.jimtec.anagramsolver"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (keystoreProps != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Word lists are plain text; keep them uncompressed so they load straight from the APK.
    androidResources { noCompress += "txt" }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyWebFiles, CopyWebFiles::outputDir)
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.core.ktx)
    implementation(libs.webkit)
}
