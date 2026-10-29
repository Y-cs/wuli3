plugins { base }

description = "Wuli3 interactive architecture documentation site."
val outputSite = layout.buildDirectory.dir("docs")
val docsBase = providers.environmentVariable("DOCS_BASE").orElse("/")

val npmCi = tasks.register<Exec>("npmCi") {
    group = "documentation"
    description = "Installs the locked VitePress documentation dependencies."
    workingDir(layout.projectDirectory)
    commandLine("npm", "ci")
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
}

val buildDocs = tasks.register<Exec>("buildDocs") {
    group = "documentation"
    description = "Builds the VitePress site into the Gradle build directory."
    dependsOn(npmCi)
    workingDir(layout.projectDirectory)
    environment("DOCS_OUT_DIR", outputSite.get().asFile.absolutePath)
    environment("DOCS_BASE", docsBase.get())
    commandLine("npm", "run", "docs:build")
    inputs.files(fileTree("src") {
        exclude(".vitepress/cache/**", ".vitepress/*.timestamp*")
    })
    inputs.files("package.json", "package-lock.json")
    inputs.property("docsBase", docsBase)
    outputs.dir(outputSite)
}

tasks.named("assemble") { dependsOn(buildDocs) }
tasks.register("checkDocs") {
    group = "verification"
    description = "Builds the VitePress documentation site."
    dependsOn(buildDocs)
}
tasks.named("check") { dependsOn("checkDocs") }
