plugins {
    id("mc")
}

version = rootProject.version

dependencies {
    listOf("uniforms", "shaderpack", "gl").forEach {
        include(api("com.ventooth:swansong-$it:${property("swansong_version")}")!!)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/cera.classtweaker")
}
