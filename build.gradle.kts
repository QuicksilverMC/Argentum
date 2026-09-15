plugins {
    id("mc")
}

version = "1.0.0"

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

val testmod = sourceSets.create("testmod")
testmod.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
testmod.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath

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
    }
    runs {
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
