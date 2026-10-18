plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")

    implementation(gradleApi())
}
