plugins {
    id("mc")
}

version = rootProject.version

base.archivesName = "${rootProject.name}-${project.name}"

dependencies {
    compileOnly(project(path = ":cera", configuration = "namedElements"))
}
