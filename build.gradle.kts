group = "com.github.kropp.gradle"
version = "0.2.1"

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.9.9"
}

gradlePlugin {
    (plugins) {
        "thanks" {
            id = "thanks"
            implementationClass = "com.github.kropp.gradle.thanks.ThanksPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/kropp/gradle-plugin-thanks"
    vcsUrl = "https://github.com/kropp/gradle-plugin-thanks"
    description = "Say thanks to the libraries you depend on in form of a Github star"
    tags = listOf("github", "star")
    (plugins) {
        "thanks" {
            id = "com.github.kropp.thanks"
            displayName = "Thanks"
        }
    }
}