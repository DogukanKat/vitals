plugins {
    `java-library`
    application
}

dependencies {
    implementation(project(":vitals-core"))
    implementation(project(":vitals-static-engine"))
    implementation(project(":vitals-rules-jpa"))
    implementation(project(":vitals-rules-spring"))
    implementation(project(":vitals-rules-kafka"))
    implementation(project(":vitals-rules-redis"))
    implementation(project(":vitals-rules-jvm"))
    implementation(rootProject.libs.picocli)
    runtimeOnly(rootProject.libs.logback.classic)
}

application {
    mainClass.set("dev.vitals.cli.VitalsCli")
    applicationName = "vitals"
}
