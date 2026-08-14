plugins {
    java
    war
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.middleproject"
version = "0.1.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-cloudwatch2")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(platform("software.amazon.awssdk:bom:2.31.63"))
    implementation("software.amazon.awssdk:scheduler")
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:sesv2")
    implementation("com.networknt:json-schema-validator:1.5.6")
    testRuntimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootWar { archiveFileName.set("ROOT.war") }
tasks.bootJar { enabled = false }
tasks.test { useJUnitPlatform() }
