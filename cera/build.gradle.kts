plugins {
    id("mc")
}

version = rootProject.version

repositories {
    exclusiveContent {
        forRepository { mavenLocal() }
        filter {
            includeGroup("com.ventooth.swansong")
        }
    }
}

dependencies {
    listOf("uniforms", "shaderpack", "gl").forEach {
        include(implementation("com.ventooth.swansong:$it:${property("swansong_version")}")!!)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/cera.classtweaker")
}
