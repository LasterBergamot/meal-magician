// The foojay resolver lets Gradle download the Java toolchain (Temurin 25) on a machine that
// only has another JDK. Plugin versions in settings can't come from the version catalog.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "meal-magician"
