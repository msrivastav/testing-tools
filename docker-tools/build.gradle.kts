plugins {
    java
}

group = "me.msri"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        isAllowInsecureProtocol = true
        setUrl("http://repo.gradle.org/gradle/libs-releases-local")
    }
}

dependencies {
    implementation("com.github.docker-java:docker-java:3.2.12")
    implementation ("org.springframework.boot:spring-boot-starter:2.6.3")

    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    implementation("org.gradle:gradle-tooling-api:7.3-20210825160000+0000")
    implementation("org.slf4j:slf4j-api:1.7.35")


    testCompileOnly("org.projectlombok:lombok:1.18.22")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.22")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
