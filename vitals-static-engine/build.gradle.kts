plugins {
    `java-library`
}

dependencies {
    api(project(":vitals-core"))
    implementation(rootProject.libs.javaparser.symbol.solver)
    implementation(rootProject.libs.bytebuddy)
    implementation(rootProject.libs.snakeyaml)
}
