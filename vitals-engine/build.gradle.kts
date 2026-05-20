plugins {
    `java-library`
}

dependencies {
    api(project(":vitals-core"))
    implementation(project(":vitals-static-engine"))
    implementation(project(":vitals-rules-jpa"))
    implementation(project(":vitals-rules-spring"))
    implementation(project(":vitals-rules-kafka"))
    implementation(project(":vitals-rules-redis"))
    implementation(project(":vitals-rules-jvm"))
    implementation(rootProject.libs.slf4j.api)
    testImplementation(rootProject.libs.json.schema.validator)
}

// The published JSON Schema is the single source of truth; expose it on the
// test classpath so JsonReporterTest validates against the real contract.
sourceSets {
    test {
        resources {
            srcDir(rootProject.file("docs/schema"))
        }
    }
}
