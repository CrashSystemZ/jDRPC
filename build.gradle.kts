import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "fun.crashsystem"
version = "1.2.1"

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.apache.commons:commons-lang3:3.18.0")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:none", true)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()

    dependencies {
        exclude(dependency("org.apache.logging.log4j:log4j-api:.*"))
    }

    relocate("com.google.gson", "fun.crashsystem.jdrpc.libs.com.google.gson")
    relocate("org.apache.commons.lang3", "fun.crashsystem.jdrpc.libs.org.apache.commons.lang3")
}