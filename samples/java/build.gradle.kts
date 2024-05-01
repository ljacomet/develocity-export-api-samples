plugins {
    id("java")
    id("groovy")
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-sse")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.17.0"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("org.apache.groovy:groovy:4.0.21")
    implementation("org.apache.groovy:groovy-json:4.0.21")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

application {
    mainClass.set("com.develocity.export.ExportApiJavaExample")
}
