import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
    jacoco
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

// ---------------------------------------------------------------------------
// Tier 3: client gametests (a real Minecraft client, real GL context, real world)
// ---------------------------------------------------------------------------
//
// This is the only tier that can answer this mod's central question: do the two
// mixins actually APPLY and FUNCTION in a running client? Mixin apply failures
// are a runtime-only phenomenon - `mixins.easiervillagertrading.json` declares
// "required": true with "injectors": {"defaultRequire": 1}, so a target rename
// or a moved injection point kills the client at class-load time while the
// build stays green and every other tier passes. Tier 1 (fabric-loader-junit)
// bootstraps the game but never applies client mixins or opens a screen.
//
// fabric-client-gametest-api-v1 first ships around fabric-api 0.106 / MC 1.21.2,
// so only the 1.21.4 and 26.2 Fabric cells can run this. Forge and NeoForge have
// no equivalent reachable from Architectury Loom (see the junit-fml note below
// for the same underlying ModDevGradle-only limitation).
val clientGameTestSupported = mod.isFabric &&
    stonecutter.eval(stonecutter.current.version, ">=1.21.4")

if (clientGameTestSupported) {
    extensions.configure<net.fabricmc.loom.api.fabricapi.FabricApiExtension>("fabricApi") {
        configureTests {
            createSourceSet.set(true)
            modId.set("easiervillagertrading-gametest")
            enableGameTests.set(false)
            enableClientGameTests.set(true)
        }
    }

    // Registering the gametest mod makes Loom stop inferring the mod under test,
    // so it has to be declared explicitly. Unlike the sibling repos this one needs
    // BOTH source sets: splitEnvironmentSourceSets() puts every class the test
    // actually exercises (BetterGuiMerchant and both mixins) in "client", while
    // AutoTrade and EasierVillagerTradingConfig stay in "main". Registering only
    // "main" produces a dev-run mod with no mixin config and no entry point - the
    // client boots, the test opens a vanilla MerchantScreen, and the flagship
    // assertion fails for a reason that has nothing to do with the mod.
    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom") {
        mods.create(mod.prop("id", "easiervillagertrading")) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    // The generated "gametest" source set is wired against "main" only, and on a
    // split-environment Fabric cell "main" has no client-side Minecraft on its
    // compile classpath at all - not MerchantScreen, not Minecraft, not Screen.
    // The test also needs to name BetterGuiMerchant directly (it is the flagship
    // assertion: waitForScreen(BetterGuiMerchant.class)). Hand the gametest source
    // set the client source set's full classpath plus its output.
    val clientSourceSet = sourceSets["client"]
    sourceSets.named("gametest") {
        compileClasspath += clientSourceSet.compileClasspath + clientSourceSet.output
        runtimeClasspath += clientSourceSet.runtimeClasspath + clientSourceSet.output
    }

    // Two collisions with Stonecraft's own `runs.all {}` block, both of which
    // apply to the clientGameTest run config Loom generates and neither of which
    // Stonecraft knows about. afterEvaluate because Stonecraft configures these
    // in its own plugin-apply phase; we have to be last.
    afterEvaluate {
        extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom") {
            runs.named("clientGameTest") {
                // Stonecraft appends --username=developer; Loom's clientGameTest
                // config already supplies its own. joptsimple rejects the duplicate
                // outright (MultipleArgumentsForOptionException) and the client dies
                // before the first frame.
                programArguments.set(
                    programArguments.get().filterNot { it.startsWith("--username=") }
                )
                // Stonecraft's setRunDir points every run at the repo-root ../../run.
                // Loom's own deleteGameTestRunDir task wipes whatever runDirectory
                // resolves to, which would delete the shared dev-client run directory.
                runDirectory.set(layout.buildDirectory.dir("run/clientGameTest"))
            }
        }
    }

    // `check` cannot run the client gametest itself (it needs a display; CI runs it
    // under xvfb as a separate job), but it can and should refuse to pass if the
    // test source no longer compiles against this cell's API.
    tasks.named("check") { dependsOn(tasks.named("compileGametestJava")) }
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

dependencies {
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Tier 1 "loaded game" testing (Fabric cells only). fabric-loader-junit stands a real
    // Fabric loader up inside the JUnit worker, which is what makes it legal to call
    // SharedConstants.tryDetectVersion() + Bootstrap.bootStrap() and then assert against
    // genuinely loaded game data - see src/test/java/.../LoadedGameTest.java and PLAN.md.
    // NeoForge/Forge have no Loom-compatible equivalent (see the junit-fml note below).
    if (mod.isFabric) {
        testImplementation("net.fabricmc:fabric-loader-junit:0.19.3")
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// NeoForge's transitive net.neoforged.fancymodloader:junit-fml (<=9.0.18, pulled in by the
// 1.21.4 target) auto-registers a LauncherSessionListener that looks up a run-config file
// "mainargs.txt" via a relative path that doesn't resolve under Gradle's test-worker working
// directory, failing `test` at JUnit-launcher startup with NoSuchFileException: mainargs.txt.
// junit-fml exists to bootstrap FML for *gametests*; this repo's tests are plain pure-logic
// JUnit tests that need none of that, so it's excluded from the test runtime classpath.
// Same fix as the sibling critical-orientation / simple-utilities-mod / ToroHealth repos.
//
// Caveat, recorded 2026-08-13 so nobody re-derives it: excluding junit-fml is the
// right call *for these repos*, not a universal one. junit-fml is precisely
// NeoForge's own loaded-test bootstrap - it is what stands FML up so a test can
// run against a real, loaded game. NeoForge's supported loaded-test path
// (`neoForge { unitTest { enable(); testedMod = ... } }`, the `testframework`
// artifact, `@ExtendWith(EphemeralTestServerProvider.class)`, `runGameTestServer`)
// is ModDevGradle-only, and this repo builds on Architectury Loom via Stonecraft,
// so that path is unavailable here regardless. Excluding junit-fml therefore costs
// nothing today. If a cell is ever migrated to ModDevGradle, this exclusion must
// be revisited before writing any loaded NeoForge test - it would silently disable
// the very bootstrap such a test depends on.
if (mod.isNeoforge) {
    configurations.named("testRuntimeClasspath") {
        exclude(group = "net.neoforged.fancymodloader", module = "junit-fml")
    }
}

// JaCoCo scope: this mod is almost entirely mixin/GUI/loader-entry-point code, all of which
// touches real Minecraft client classes (MerchantScreen, MerchantMenu, FabricLoader, Mixin
// injection targets) at class-load or call time and is genuinely untestable headless - see
// CLAUDE.md/PLAN.md "Testing" for the full reasoning per class. Only
// EasierVillagerTradingConfig (plain-old Properties-file I/O, no Minecraft import at all) is
// in scope for the 100% line-coverage bar. AutoTrade is a bodiless interface (no bytecode
// instructions to cover either way) and is left unexcluded since it can't affect the ratio.
val jacocoExcludes = listOf(
    "de/guntram/mcmod/easiervillagertrading/BetterGuiMerchant.class",
    "de/guntram/mcmod/easiervillagertrading/BetterGuiMerchant$*.class",
    "de/guntram/mcmod/easiervillagertrading/EasierVillagerTrading.class",
    "de/guntram/mcmod/easiervillagertrading/EasierVillagerTrading$*.class",
    "de/guntram/mcmod/easiervillagertrading/mixins/*.class",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Forge's pack.mcmeta generation task writes into the main source set's resources output
// without declaring that as a tracked task output, which Gradle's task validation flags as
// an undeclared ("implicit") dependency on :compileTestJava (which consumes sourceSets.main.output
// as part of the test compile classpath now that the "test" sourceSet/tasks exist on every
// cell). Declare the dependency explicitly so validation passes. tasks.matching(...) is a
// live/lazy filter, so this is a harmless no-op on loaders that don't have a
// generatePackMCMetaJson task (e.g. Fabric). Same fix as critical-orientation's build.gradle.kts.
tasks.matching { it.name == "compileTestJava" }.configureEach {
    dependsOn(tasks.matching { it.name == "generatePackMCMetaJson" })
}
