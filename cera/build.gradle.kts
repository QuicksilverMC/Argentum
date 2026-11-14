plugins {
    id("mc")
}

version = rootProject.version

repositories {
    exclusiveContent {
        forRepository { maven("https://mvn.ventooth.com/releases") }
        filter {
            includeGroup("com.ventooth")
        }
    }
}

dependencies {
    listOf("uniforms", "shaderpack", "gl").forEach {
        include(implementation("com.ventooth:swansong-$it:${property("swansong_version")}")!!)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/cera.classtweaker")
}
