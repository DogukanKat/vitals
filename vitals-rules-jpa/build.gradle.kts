plugins {
    `java-library`
}

dependencies {
    api(project(":vitals-core"))
    implementation(project(":vitals-static-engine"))
    implementation(rootProject.libs.javaparser.symbol.solver)
}
