plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

android {
    namespace = "com.kitsugi.archivetest"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId = "com.kitsugi.archivetest"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/production"))
    sourceSets["androidTest"].assets.srcDir(layout.buildDirectory.dir("generated/probeAssets"))
}

val copyProduction by tasks.registering(Copy::class) {
    from("../../../app/src/main/java/com/kitsugi/animelist/data/remote/AniSubArchive.kt")
    from("../../../app/src/main/java/com/kitsugi/animelist/core/player/AnisubMatching.kt")
    from("../../../app/src/main/java/com/kitsugi/animelist/core/player/SubtitleEncoding.kt")
    into(layout.buildDirectory.dir("generated/production"))
}
tasks.named("preBuild") { dependsOn(copyProduction) }
dependencies {
    implementation("me.zhanghai.android.libarchive:library:1.1.6")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
