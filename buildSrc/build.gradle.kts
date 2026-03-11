plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")

    implementation(gradleApi())
}
