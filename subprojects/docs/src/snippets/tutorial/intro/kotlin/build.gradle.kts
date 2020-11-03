tasks.register("hello") {
    doLast {
        println("Hello world!")
    }
}
tasks.register("intro") {
    dependsOn(tasks.hello)
    doLast {
        println("I'm Gradle")
    }
}
