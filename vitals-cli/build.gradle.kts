plugins {
    `java-library`
    application
}

dependencies {
    implementation(project(":vitals-engine"))
    implementation(rootProject.libs.picocli)
    runtimeOnly(rootProject.libs.logback.classic)
}

application {
    mainClass.set("dev.vitals.cli.VitalsCli")
    applicationName = "vitals"
}
