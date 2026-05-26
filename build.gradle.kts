plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.+"
    java
}

apply(plugin = "net.fabricmc.fabric-loom")

version = project.property("mod_version")!!
group = project.property("maven_group")!!

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
    }
}

loom {
    splitEnvironmentSourceSets()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("com.terraformersmc:modmenu:18.0.0-beta.1")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    withType<JavaCompile> {
        options.release.set(25)
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.base.archivesName.get()}" }
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("reserved-slots")
    versionNumber.set(project.version as String)
    versionType.set("release")
    uploadFile.set(tasks.named("remapJar"))
    gameVersions.addAll("26.1")
    loaders.addAll("fabric")
    changelog.set(System.getenv("CHANGELOG") ?: "Release ${project.version}")
    syncBodyFrom.set(rootProject.file("README.md").readText())
    dependencies {
        required.project("fabric-api")
    }
}