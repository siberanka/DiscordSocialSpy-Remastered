plugins {
    java
}

group = "net.siberanka"
version = "2.0.0"
val pluginVersion = version.toString()

val modernApi = providers.gradleProperty("apiLine").orNull == "modern"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(if (modernApi) 25 else 21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotSnapshots"
        content {
            includeGroup("org.spigotmc")
            includeGroup("net.md-5")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "paper"
        content {
            includeGroup("io.papermc.paper")
            includeGroup("net.md-5")
        }
    }
    maven("https://libraries.minecraft.net/") {
        name = "minecraftLibraries"
        content { includeGroup("com.mojang") }
    }
}

dependencies {
    val apiDependency = if (modernApi) {
        "io.papermc.paper:paper-api:26.2.build.84.+"
    } else {
        "org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT"
    }
    if (modernApi) {
        compileOnly(apiDependency)
    } else {
        compileOnly(apiDependency)
    }

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(apiDependency)
}

tasks.withType<JavaCompile>().configureEach {
    // Modern CI compiles against Java 25 API classes only as a source-compatibility check.
    // Release artifacts always use the default legacy line and Java 11 bytecode.
    options.release.set(if (modernApi) 25 else 11)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-deprecation", "-Xlint:-processing", "-Xlint:-classfile"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("DiscordSocialSpy")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Built-By" to "siberanka"
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("verifyCompatibility") {
    group = "verification"
    description = "Builds the plugin against the selected API line (legacy by default)."
    dependsOn(tasks.check, tasks.jar)
}
