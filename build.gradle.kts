import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    kotlin("jvm") version "2.3.0"
    alias(libs.plugins.loom)
}

loom {
    runs {
        named("client") {
            client()
            configName = "Minecraft Client - Fabric"
            ideConfigGenerated(true)
            runDir("run")
            vmArgs("-Ddevauth.enabled=true")
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://maven.teamresourceful.com/repository/maven-public")
    maven(url = "https://repo.essential.gg/repository/maven-public")
    maven(url = "https://maven.msrandom.net/repository/root")
    maven(url = "https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    "minecraft"(libs.minecraft)

    "implementation"(libs.fabric.loader)
    "implementation"(libs.fabric.api)
    "implementation"(libs.fabric.kotlin)

    "runtimeOnly"(libs.devauth)

    "implementation"(libs.vigilance) {
        "include"(this)
        isTransitive = false
    }
    "implementation"(libs.elementa) {
        "include"(this)
        isTransitive = false
    }
    "implementation"(libs.universalcraft) {
        "include"(this)
        exclude("org.jetbrains.kotlinx")
        exclude("org.jetbrains.kotlin")
        exclude("net.fabricmc")
    }

    "implementation"(libs.resourceful.lib) {
        "include"(this)
    }
    "implementation"(libs.olympus) {
        "include"(this)
    }

    implementation(libs.jukebox) {
        "include"(this)
        isTransitive = false
    }
    implementation(libs.mediaInterfaceCore) {
        "include"(this)
    }
    implementation(libs.mediaInterfaceWindows) {
        "include"(this)
    }
    implementation(libs.ktor.cio) {
        exclude("org.jetbrains.kotlinx")
        exclude("org.jetbrains.kotlin")
    }
    implementation(libs.ktor.core) {
        exclude("org.jetbrains.kotlinx")
        exclude("org.jetbrains.kotlin")
        exclude("org.slf4j")
    }
    libs.bundles.ktor.get().forEach {
        "include"(provider { it })
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<RemapJarTask> {
    archiveClassifier.set("")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}


val prismModsDir = file("C:/Users/mattwhyy/AppData/Roaming/PrismLauncher/instances/26.1.2/minecraft/mods")
val remappedCraftifyJar = tasks.named<RemapJarTask>("remapJar")

tasks.named("build") {
    doLast {
        if (!prismModsDir.isDirectory) {
            throw GradleException("Prism mods directory does not exist: ${prismModsDir.absolutePath}")
        }

        val builtJar = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar").get().asFile\n        if (!builtJar.isFile) {\n            throw GradleException("Built Craftify jar not found: ${builtJar.absolutePath}")\n        }

        prismModsDir.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.equals("jar", ignoreCase = true) &&
                    it.name.startsWith("craftify-", ignoreCase = true)
            }
            ?.forEach { oldJar ->
                if (!oldJar.delete()) {
                    throw GradleException("Could not replace existing Craftify jar: ${oldJar.absolutePath}")
                }
            }

        builtJar.copyTo(prismModsDir.resolve(builtJar.name), overwrite = true)
        println("Deployed ${builtJar.name} to ${prismModsDir.absolutePath}")
    }
}
