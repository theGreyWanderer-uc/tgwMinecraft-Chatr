plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
//    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.thegreywanderer_uc"
version = "1.0.0"
description = "chatr"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    implementation("com.google.code.gson:gson:2.11.0")
    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        filteringCharset = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    // Configure reobfJar to use jar as input
    reobfJar {
        inputJar.set(jar.flatMap { it.archiveFile })
    }
}