plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(localGroovy())
    testImplementation("junit:junit:4.13.2")
}
