plugins {
    id("mc")
    `maven-publish`
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

val releaseBuild = providers.gradleProperty("release").isPresent
version = providers.environmentVariable("GITHUB_SHA")
    .filter { !releaseBuild }
    .flatMap { sha -> providers.gradleProperty("mod_version").map { "$it+${sha.take(7)}" } }
    .orElse(providers.gradleProperty("mod_version"))
    .get()

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

val testmod = sourceSets.create("testmod")
testmod.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
testmod.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath

val all = sourceSets.create("all")
all.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath

gradle.taskGraph.whenReady {
    allTasks.filter { it.name == "net.fabricmc.devlaunchinjector.Main.main()" }.forEach {
        it.notCompatibleWithConfigurationCache("loom weird?")
    }
}

loom {
    accessWidenerPath = file("src/main/resources/argentum.classtweaker")

    mods {
        create("argentum-font-test") {
            sourceSet(testmod)
        }
        create("argentum-extras") {
            sourceSet("main", ":extras")
        }
        create("cera") {
            sourceSet("main", ":cera")
        }
    }
    runs {
        create("allClient") {
            inherit(getByName("client"))
            sourceSet = all.name
            displayName = "Client (All Mods)"
        }
        create("fontTestClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "Font Visual Test"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.disableFontBatching", findProperty("disableFontBatching")?.toString() ?: "false")
            systemProperties.put("argentum.fontTestVariant", findProperty("fontTestVariant")?.toString() ?: "batched")
        }
        create("fontTestVanillaClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "Font Visual Test (Vanilla)"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.disableFontBatching", "true")
            systemProperties.put("argentum.fontTestVariant", "vanilla")
        }
        create("itemTestClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "GUI Item Visual Test (Atlas)"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.itemTestVariant", "atlas")
            systemProperties.put("argentum.itemTestGlint", findProperty("itemTestGlint")?.toString() ?: "false")
        }
        create("itemTestVanillaClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "GUI Item Visual Test (Vanilla)"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.itemTestVariant", "vanilla")
            systemProperties.put("argentum.itemTestGlint", findProperty("itemTestGlint")?.toString() ?: "false")
        }
        create("guiBenchmarkBatchedClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "GUI Batch Benchmark (Batched)"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.guiBenchmark", "true")
            systemProperties.put("argentum.guiBenchmarkBatched", "true")
        }
        create("guiBenchmarkUnbatchedClient") {
            inherit(getByName("client"))
            sourceSet = testmod.name
            displayName = "GUI Batch Benchmark (Unbatched)"
            jvmArguments.add("-XstartOnFirstThread")
            systemProperties.put("argentum.guiBenchmark", "true")
            systemProperties.put("argentum.guiBenchmarkBatched", "false")
        }
    }
}

dependencies {
    include(api("org.joml:joml:1.10.5")!!)
    include(api("org.embeddedt.celeritas:celeritas-common:${property("celeritas_version")}")!!)

    "allRuntimeOnly"(project(path = ":extras", configuration = "namedElements"))
    "allRuntimeOnly"(project(path = ":cera", configuration = "namedElements"))
}

tasks.named("runFontTestClient") {
    mustRunAfter("runFontTestVanillaClient")
}

tasks.named("runGuiBenchmarkBatchedClient") {
    mustRunAfter("runGuiBenchmarkUnbatchedClient")
}

fun registerFontComparison(name: String, expected: String, actual: String) = tasks.register<Exec>(name) {
    dependsOn("runFontTestVanillaClient", "runFontTestClient")
    commandLine("cmp", "run/screenshots/$expected", "run/screenshots/$actual")
}

val verifyFontBeforeReload = registerFontComparison(
    "verifyFontBeforeReload",
    "font-vanilla-before-reload.png",
    "font-batched-before-reload.png"
)
val verifyFontAfterReload = registerFontComparison(
    "verifyFontAfterReload",
    "font-vanilla-after-reload.png",
    "font-batched-after-reload.png"
)
val verifyFontReload = registerFontComparison(
    "verifyFontReload",
    "font-batched-before-reload.png",
    "font-batched-after-reload.png"
)

tasks.named("runItemTestClient") {
    mustRunAfter("runItemTestVanillaClient")
}

fun registerItemComparison(name: String, expected: String, actual: String): TaskProvider<Task> {
    val expectedFile = layout.projectDirectory.file("run/screenshots/$expected").asFile
    val actualFile = layout.projectDirectory.file("run/screenshots/$actual").asFile

    return tasks.register(name) {
        dependsOn("runItemTestVanillaClient", "runItemTestClient")
        doLast {
            val a = javax.imageio.ImageIO.read(expectedFile)
            val b = javax.imageio.ImageIO.read(actualFile)
            require(a.width == b.width && a.height == b.height) {
                "$expected and $actual differ in size: ${a.width}x${a.height} vs ${b.width}x${b.height}"
            }

            var differing = 0
            var worst = 0
            for (y in 0 until a.height) {
                for (x in 0 until a.width) {
                    val p = a.getRGB(x, y)
                    val q = b.getRGB(x, y)
                    if (p == q) continue
                    differing++
                    for (shift in intArrayOf(16, 8, 0)) {
                        val delta = Math.abs(((p shr shift) and 0xFF) - ((q shr shift) and 0xFF))
                        if (delta > worst) worst = delta
                    }
                }
            }

            val fraction = differing.toDouble() / (a.width * a.height)
            println("$actual: %d px differ (%.4f%%), worst channel delta %d".format(differing, fraction * 100, worst))
            require(worst <= 1) { "$actual differs from $expected by up to $worst per channel; expected rounding only" }
            require(fraction < 0.0005) { "$actual differs in %.4f%% of pixels; expected under 0.05%%".format(fraction * 100) }
        }
    }
}

val verifyItemsFirst = registerItemComparison(
    "verifyGuiItemsFirst", "item-vanilla-first.png", "item-atlas-first.png"
)
val verifyItemsSecond = registerItemComparison(
    "verifyGuiItemsSecond", "item-vanilla-second.png", "item-atlas-second.png"
)

tasks.register("verifyGuiItemRendering") {
    group = "verification"
    dependsOn(verifyItemsFirst, verifyItemsSecond)
}

tasks.register("verifyFontRendering") {
    group = "verification"
    dependsOn(verifyFontBeforeReload, verifyFontAfterReload, verifyFontReload)
}

tasks.register("benchmarkGuiBatching") {
    group = "verification"
    dependsOn("runGuiBenchmarkUnbatchedClient", "runGuiBenchmarkBatchedClient")
}

tasks.check {
    dependsOn("compileTestmodJava")
}

tasks.remapJar {
    manifest.attributes(
        "Celeritas-Version" to project.property("celeritas_version") as String
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            setArtifacts(listOf(tasks.remapJar))
        }
    }
    repositories {
        maven("https://maven.taumc.org/releases") {
            credentials(PasswordCredentials::class) {
                username = "rdh"
                password = System.getenv("TAUMC_MAVEN_PASSWORD")
            }
        }
    }
}

publishMods {
    file.set(tasks.remapJar.flatMap { it.archiveFile })
    type.set(STABLE)
    modLoaders.add("ornithe")
    dryRun.set(providers.gradleProperty("publishDryRun").map(String::toBoolean).orElse(false))

    val argentumChangelog = providers.gradleProperty("argentumChangelog")
    val extrasChangelog = providers.gradleProperty("extrasChangelog")
    val ceraChangelog = providers.gradleProperty("ceraChangelog")

    val modrinthDefaults = modrinthOptions {
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        minecraftVersions.add(providers.gradleProperty("minecraft_version"))
    }

    modrinth("modrinthArgentum") {
        from(modrinthDefaults)
        projectId.set("QRMV06RW")
        displayName.set("Argentum ${project.version}")
        changelog.set(argentumChangelog)
        requires("osl")
        optional("pylon")
        optional("moehreag-legacy-lwjgl3")
    }
    modrinth("modrinthExtras") {
        from(modrinthDefaults)
        file(project(":extras"))
        projectId.set("Tc8Lbsu7")
        displayName.set("Argentum Extras ${project.version}")
        changelog.set(extrasChangelog)
        requires { id.set("QRMV06RW") }
    }
    modrinth("modrinthCera") {
        from(modrinthDefaults)
        file(project(":cera"))
        projectId.set("z4ctq7MO")
        displayName.set("Cera ${project.version}")
        changelog.set(ceraChangelog)
        requires { id.set("QRMV06RW") }
    }
    github {
        accessToken.set(providers.environmentVariable("GITHUB_TOKEN"))
        repository.set("QuicksilverMC/Argentum")
        commitish.set(providers.environmentVariable("GITHUB_SHA").orElse("main"))
        tagName.set("v${project.version}")
        displayName.set(project.version.toString())
        additionalFile(project(":extras"))
        additionalFile(project(":cera"))
        changelog.set(argentumChangelog.zip(extrasChangelog) { argentum, extras ->
            "## Argentum\n\n$argentum\n\n## Argentum Extras\n\n$extras"
        }.zip(ceraChangelog) { changelog, cera ->
            "$changelog\n\n## Cera\n\n$cera"
        })
    }
}
