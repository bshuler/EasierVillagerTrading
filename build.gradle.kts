import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

// Without one of these two branches, Gradle never learns that src/client/java
// exists at all: there is no convention-based "client" sourceSet like
// "main"/"test", so files under it are silently skipped by compileJava - no
// error, no warning, just absent from every shipped jar (confirmed via
// unzip -l: every mixin/logic class, and even the fabric.mod.json entrypoint
// class, were missing from every cell before this was added).
//
// Fabric only: use Architectury Loom's real environment split, which wires
// src/client/java into a genuine "client" sourceSet with its own
// Minecraft-only classpath and mixin side tagging.
//
// Forge AND NeoForge (confirmed by hitting the exact exception on both:
// "Using Forge with split jars is not supported!" and "Using NeoForge with
// split jars is not supported!" - isForgeLike() covers both): neither ships
// split client/server jars, so src/client/java is merged straight into the
// main sourceSet instead. The mods.toml/neoforge.mods.toml `side = "CLIENT"`
// restriction still keeps it client-only at runtime even though it isn't
// split at compile time.
if (mod.isForgeLike) {
    // Merely creating a sourceSet named "client" is not enough: Stonecutter
    // does register stonecutterGenerateClient/compileClientJava tasks for it
    // (confirmed via `tasks --all`), but nothing in the default task graph
    // depends on them, and a freshly created sourceSet has no classpath
    // wiring to "main" at all - confirmed by a build that went green and
    // still shipped a jar with only the src/main/java classes (AutoTrade,
    // EasierVillagerTradingConfig), silently missing every client class.
    // Wire it up by hand:
    sourceSets.create("client") {
        java.srcDir("src/client/java")
    }
    val clientSourceSet = sourceSets.getByName("client")
    val mainSourceSet = sourceSets.main.get()
    // Forge/NeoForge never split client/server jars, so main's own
    // compileClasspath already carries the full (client-inclusive)
    // Minecraft artifact - reuse it verbatim instead of re-deriving one,
    // and add main's own compiled output so client code can call the
    // MC-free classes (AutoTrade, EasierVillagerTradingConfig) that live
    // in src/main/java.
    // classesDirs only, NOT the full sourceSet output: pulling in
    // mainSourceSet.output (which also carries its resourcesDir) put
    // build/resources/main on compileClientJava's classpath, and Forge's own
    // McMetaCreation task (generatePackMCMetaJson) writes pack.mcmeta into
    // that exact directory without Gradle knowing about it - triggering
    // "Property has implicit dependency" once compileClientJava started
    // touching that directory too (gotcha (h) from the porting brief,
    // confirmed for real here). Compilation never needs resources anyway.
    clientSourceSet.compileClasspath = mainSourceSet.compileClasspath + project.files(mainSourceSet.output.classesDirs)
    clientSourceSet.runtimeClasspath = mainSourceSet.runtimeClasspath + mainSourceSet.output
    // A custom-named sourceSet does not automatically inherit "main"'s
    // dependency configurations (that extendsFrom wiring is special-cased
    // by the java plugin for "test" only) - without this, any dependency
    // (incl. the mixin annotation processor) declared against
    // implementation/annotationProcessor would be invisible to
    // compileClientJava.
    configurations.named("clientImplementation") { extendsFrom(configurations["implementation"]) }
    configurations.named("clientCompileOnly") { extendsFrom(configurations["compileOnly"]) }
    configurations.named("clientAnnotationProcessor") { extendsFrom(configurations["annotationProcessor"]) }
    configurations.named("clientRuntimeOnly") { extendsFrom(configurations["runtimeOnly"]) }
    // Fold the client sourceSet's compiled output into the same jar
    // Forge/NeoForge expect a single merged mod jar from (there is no
    // split-jar concept to put them in separately). `from(...)` alone makes
    // Gradle infer the right task dependency (jar -> compileClientJava);
    // deliberately NOT also wiring classes.dependsOn(clientClasses) - that
    // direction creates classes -> clientClasses -> compileClientJava ->
    // classes, a real circular dependency (compileClientJava's classpath
    // includes main's output, so Gradle already infers
    // compileClientJava -> classes on its own).
    tasks.named<Jar>("jar") { from(clientSourceSet.output) }
} else {
    loom {
        splitEnvironmentSourceSets()
    }
}

modSettings {
    clientOptions {
        fov = 90
        guiScale = 2
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }
}
