plugins {
    java
}

group = "me.msri"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.docker-java:docker-java:3.2.12")

    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")

    testCompileOnly("org.projectlombok:lombok:1.18.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.20")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
