# CLAUDE.md — EasierVillagerTrading

## What this mod does

Client-side Minecraft mod. Changes the meaning of the buttons in the villager
trade-selection GUI:

- **Click** a trade → execute it once (vanilla just *prepares* the trade).
- **Shift-click** a trade → execute it repeatedly until the villager locks the
  trade or you run out of trade-input items.
- **Ctrl-click** a trade → prepare only (vanilla default behaviour).

Purely client-side: no server component, works on vanilla servers (an
anti-cheat plugin on the server side could theoretically block the rapid slot
clicks, but there is nothing this mod can do about that).

## Fork provenance — READ BEFORE PORTING

This repo is a fork of **gbl/EasierVillagerTrading** (Guntram Blohm), MIT
licensed. Upstream is `git remote upstream`, kept intact — **never push to it**.

Upstream actively maintained this mod for years across many MC versions, each
as its own long-lived branch (no `main`, `HEAD` points at `fabric_1_20`):

| Upstream branch | Loader | Targets |
|---|---|---|
| `legacy_1_10_2`, `legacy_1_11`, `legacy_1_12_2` | Forge | ancient |
| `rift_1_13_2` | Rift | 1.13.2 |
| `fabric`, `fabric_1_16`, `fabric_1_16_1_fix`, `fabric_1_17` | Fabric | 1.16–1.17 |
| `fabric_1_18` | Fabric | **1.18.2** |
| `fabric_1_19` | Fabric | **1.19.3** (≈1.19.4) |
| `fabric_1_20` (upstream `HEAD`) | Fabric | 1.20 → 1.20.4 (last commit) |

The local `main` branch (renamed from the original ancient `master`, a Forge
1.12 / ForgeGradle 2.3 tree) is **not** the base for modernization work — per
the house fork rule, all porting starts from upstream's own Fabric ports
(`fabric_1_18` / `fabric_1_19` / `fabric_1_20`), not from hand-porting 1.12
Forge code. The `mixins/` package and `BetterGuiMerchant.trade()` logic is
**byte-for-byte identical** across `fabric_1_18` → `fabric_1_19` →
`fabric_1_20` (the only diff in that whole span is
`ItemStack.areNbtEqual` → `ItemStack.canCombine`). Treat that logic as stable;
only game/loader glue (entry points, mixin registration boilerplate, resource
files) needs touching per version.

Upstream has **no Forge, NeoForge, or post-1.20.4 Fabric branch**. Everything
past 1.20.4, and everything on Forge/NeoForge, is this fork's own porting work
— there is no newer upstream branch to crib from there.

### Upstream's own library dependency

Upstream's Fabric builds depend on **`de.guntram.mcmod:GBfabrictools`**
(gbl's own small config/ModMenu-glue library, Maven-hosted at
`https://minecraft.guntram.de/maven/`) purely to persist one boolean
(`swapShiftBehavior`, i.e. whether Shift-click or plain click means "trade
once") and to add a ModMenu options screen for it.

**This fork does not depend on GBfabrictools.** Reasons:
- It is a third-party Maven repo outside our control, of uncertain
  availability/version-matrix across 5 target MC versions.
- ModMenu's API is not stable across 1.18.2 → 26.x either.
- The feature it provides is a single boolean.

Instead, `EasierVillagerTradingConfig` (vendored, in the shared source set)
reads/writes a trivial `easiervillagertrading.properties` file in the game's
config directory directly — no external mod dependency, no ModMenu screen.
If a ModMenu options screen is wanted later, add it back per-loader behind a
`modImplementation` that's `include`d only for that version, guarded by
Stonecutter `//? if fabric`.

## Architecture (post-modernization)

Multi-version, multi-loader via **Stonecutter** (`dev.kikugie.stonecutter`)
wrapped by **Stonecraft** (`gg.meza.stonecraft`), mirroring the house
template `critical-orientation`. See that repo's CLAUDE.md/PLAN.md for the
general pattern this one copies.

```
EasierVillagerTrading/
├── settings.gradle.kts        # stonecutter{} block declares every version×loader cell
├── stonecutter.gradle.kts     # "active" version for IDE/runClient
├── build.gradle.kts           # shared Stonecraft config (applies to every cell)
├── gradle.properties          # mod.id / mod.version / mod.group
├── src/
│   ├── main/java/de/guntram/mcmod/easiervillagertrading/
│   │   ├── EasierVillagerTradingConfig.java  # vendored config (replaces GBfabrictools) — no MC import
│   │   └── AutoTrade.java                    # interface implemented by BetterGuiMerchant — no MC import
│   ├── client/java/de/guntram/mcmod/easiervillagertrading/
│   │   ├── EasierVillagerTrading.java        # loader entry point (Stonecutter-conditioned)
│   │   ├── BetterGuiMerchant.java            # core repeat-trade logic — loader/version stable
│   │   └── mixins/
│   │       ├── GuiMerchantMixin.java         # swaps in BetterGuiMerchant for vanilla MerchantScreen
│   │       └── MerchantScreenMixin.java      # hooks trade-selection to fire AutoTrade.trade()
│   └── main/resources/
│       ├── fabric.mod.json                   # Fabric metadata
│       ├── META-INF/mods.toml                # Forge metadata (Forge cells only)
│       ├── META-INF/neoforge.mods.toml        # NeoForge metadata (NeoForge cells only)
│       └── mixins.easiervillagertrading.json
└── versions/                   # Stonecutter-generated per-cell subprojects (git-ignored)
```

### Why mixins make Forge/NeoForge harder than the template

`critical-orientation` never touches vanilla internals directly (key
bindings + player yaw only, both stable-named across Yarn and Mojang
mappings). This mod's core feature — auto-repeating a trade — has no public
API; it requires **mixing into vanilla's merchant-screen classes**
(`MerchantScreen`, `MerchantScreenHandler`, `HandledScreens`,
`syncRecipeIndex`). Fabric uses **Yarn** mappings for those names; Forge and
NeoForge use **Mojang's official mappings**, which name the same classes
differently (e.g. Yarn's `MerchantScreenHandler` vs Mojang's `MerchantMenu`).
A mixin source file is tied to one mapping set — porting to Forge/NeoForge is
not "add a dependency", it's re-deriving every mixin target name from the
Mojmap deobfuscation for that MC version. See `PLAN.md` for the current
state of that effort per cell.

## Version matrix (target)

Newest stable Minecraft version per the Fabric meta API
(`https://meta.fabricmc.net/v2/versions/game`, first `"stable": true` entry —
**do not trust training-data memory here**, Mojang moved to calendar
versioning (`26.x`) partway through 2025/2026, so "1.21 is latest" is stale
knowledge). At the time this was written, newest stable = **26.2**.

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.2 (newest stable) | target | target | — (NeoForge only, post-split) |
| 1.21.4 | target | target | — |
| 1.20.1 | target | — | target |
| 1.19.4 | target | — | target |
| 1.18.2 | target | — | target |

**Loader coverage is mandatory, not "if feasible"**: every version target
must build for every loader viable on that version — Fabric + NeoForge for
1.20.5 and newer, Fabric + Forge for 1.20.4 and older. A cell may only be
marked blocked (⛔) with an exact, specific reason recorded in `PLAN.md`; it
is never silently dropped from the matrix.

**Quilt**: not a separate build target. Quilt runs Fabric jars natively via
its Quilted Fabric API compatibility layer, so every Fabric jar this repo
produces is already Quilt-compatible — there is nothing extra to build or
port for Quilt specifically.

**Single merged jar (Forgix)**: investigated per Bert's request and not
wired in this pass — see `PLAN.md` § "Single merged jar (Forgix)" for the
live-verified findings (plugin is actively released, but its documented
usage assumes static per-loader subprojects rather than Stonecutter's
per-version-per-loader ones, and merging before Forge/NeoForge reach mixin
feature parity with Fabric would hide a real behavior gap behind one
artifact). Ship per-loader jars from the single shared codebase for now;
revisit once parity is reached.

Live status/checklist: `PLAN.md`.

## Build commands

```bash
# Build every version×loader cell
./gradlew chiseledBuild

# Build one cell
./gradlew :1.20.1-fabric:build

# Run the client on the "active" version (set in stonecutter.gradle.kts)
./gradlew runClient

# Tests + JaCoCo coverage (active project only - see "Testing" below)
./gradlew ":1.21.4-fabric:test" ":1.21.4-fabric:jacocoTestReport" ":1.21.4-fabric:jacocoTestCoverageVerification"
# equivalent - check already depends on the coverage-verification task:
./gradlew ":1.21.4-fabric:check"
```

## Testing

This mod is almost entirely mixin/GUI/loader-entry-point code, which is
genuinely untestable headless (no mock Minecraft client exists for
Fabric/Forge/NeoForge GUI screens or Mixin-transformed classes). The one
exception: **`EasierVillagerTradingConfig`** has zero Minecraft imports —
plain `java.util.Properties` file I/O — and is covered by
`src/test/java/.../EasierVillagerTradingConfigTest.java` (JUnit 5) at 100%
line and branch coverage, enforced by `jacocoTestCoverageVerification`
(which `check` depends on). See `PLAN.md` § "Test coverage (Phase 2)" for
the full per-class exclusion table and reasoning.

`EasierVillagerTradingConfigTest` has no version-conditional branches, so for
coverage purposes it is enough to run it against the **active Stonecutter
project** (`1.21.4-fabric`, matching this repo's `vcsVersion`). The Tier 1
loaded tests below are the opposite case and *are* run across the whole matrix
— a bare `./gradlew test` runs every cell, which is deliberate. Switch cells
only via `./gradlew "Set active project to <cell>"`, never by hand-editing
`stonecutter.gradle.kts`.

### Loaded-game tests (Tier 1)

`src/test/java/.../LoadedGameTest.java` is different in kind from the rest of
the suite: it runs against a **real, bootstrapped Minecraft**, not mocks.
`net.fabricmc:fabric-loader-junit` stands a Fabric loader up inside the JUnit
worker, which makes it legal to call `SharedConstants.tryDetectVersion()` +
`Bootstrap.bootStrap()` in `@BeforeAll` and then assert against genuinely
loaded game data.

That matters more here than in the sibling repos, because the config class is
the *only* thing this mod can test headless — and it is not the feature. The
repeat-trade loop can't be driven without a client, but every vanilla contract
it stands on can be: `MerchantOffer.isOutOfStock()` locking after `maxUses`
(the loop's sole termination condition), the `getCostA()/getCostB()/getResult()`
accessors that paper over the 1.20.5 `ItemStack` → `ItemCost` change, the
stack-merge equivalence whose vanilla call was renamed at 1.20.5, real
`getMaxStackSize()` limits, and the loader-supplied config dir the entry point
writes to. Plus the two matrix-wide checks: a real loader discovers this mod
from its processed `fabric.mod.json`, and every declared `depends` range is
satisfiable in that specific cell (the "builds fine, refuses to load in-game"
failure mode).

- Fabric cells only (`//? if fabric` guards the whole file). NeoForge's
  equivalent bootstrap is `junit-fml`, and its supported loaded-test harness is
  ModDevGradle-only — unavailable under Architectury Loom. See the exclusion
  comment in `build.gradle.kts`.
- Verified green on **all 5 Fabric cells**, 8 tests each, read from
  `versions/*/build/test-results/test/TEST-*LoadedGameTest.xml`. Bootstrap costs
  ~20–31s per cell.
- Unlike the rest of the suite, this file **must compile on every cell**, since
  `./gradlew test` runs the whole matrix. Two version splits live in it: the
  `Registry.ITEM` → `BuiltInRegistries.ITEM` move at 1.19.3, and the 1.20.5
  `MerchantOffer(ItemStack…)` → `MerchantOffer(ItemCost…)` constructor change.
  26.x also needs an extra bootstrap step (data components are bound from
  loaded registry data rather than baked into the `Item`) — see `PLAN.md`.
- `areItemStacksMergable` is **duplicated** from `BetterGuiMerchant` rather than
  called: that class is in the `client` source set, which the split-environment
  layout keeps off the test compile classpath. Change one, change both.

```bash
./gradlew ":1.21.4-fabric:test" --tests "*LoadedGameTest"
```

### Client gametests (Tier 3)

`src/gametest/java/.../EasierVillagerTradingClientGameTest.java` launches a
**real Minecraft client** (window, GL context, render thread, integrated
server), walks up to a real wandering trader, presses the real use key and
trades. It is the only tier that touches `mixins/` or `BetterGuiMerchant` at
all — everything else in this repo is green on a build where both injectors
silently failed to apply, because **a mixin that does not apply is a runtime
event, not a compile error**.

Runs on `1.21.4-fabric` and `26.2-fabric` only; the older Fabric cells predate
`fabric-client-gametest-api-v1` and no loader-side equivalent is reachable from
Architectury Loom for Forge/NeoForge. Gated by `clientGameTestSupported` in
`build.gradle.kts`.

```bash
unset JAVA_HOME   # 26.x needs the foojay-provisioned Java 25 - see below
./gradlew :1.21.4-fabric:runClientGameTest
./gradlew :26.2-fabric:runClientGameTest
```

Three things to know before touching it:

- **`runCommand` swallows command failures.** A mis-staged world produces a
  green tick. The 26.2 game-rule rename (`doDaylightCycle` → `advance_time`,
  `doFireTick` → the *integer* `fire_spread_radius_around_player`, and four
  more) was caught only by reading the server console on a passing run. Read
  the log; do not trust the exit code. Full table in `PLAN.md`.
- **The trade button's own click is not driven** — `postButtonClick` is
  private, the buttons carry no text, and synthesising a click would hardcode
  version-varying geometry. The test calls `AutoTrade.trade(index)` directly,
  so `MerchantScreenMixin`'s wiring is covered only by a reflection check on
  the loaded `MerchantScreen`. Ctrl-click and the shift-repeat loop are
  uncovered.
- **The offer is picked at runtime.** Wandering trader offers are rolled, not
  seed-fixed; do not "simplify" this into a hardcoded index.

Both negative controls (mixin applies but never substitutes; `trade()`
no-ops) were actually run and produce the correct, distinct diagnoses — see
`PLAN.md`.

CI: `.github/workflows/build.yml` runs `chiseledBuild` over all ten cells plus
an xvfb client-gametest matrix over the two supported ones.

**Folia**: n/a — this is a 100% client-side mod with no server component.

The only JDK *installed system-wide* here is **Temurin 21**
(`/Library/Java/JavaVirtualMachines/temurin-21.jdk`). Every other JDK this
matrix needs is provisioned by Gradle's toolchain resolver (foojay, downloads
into `~/.gradle/jdks`) — older MC versions need older Java at runtime
(1.18.2/1.19.4 → Java 17), and **26.x needs Java 25**. Never install a system
JDK, never touch Homebrew for this.

**Do not `export JAVA_HOME` before running Gradle in this repo.** That pins the
*daemon's* JVM, not just the toolchain, and pinning it to 21 breaks the 26.x
cells. `unset JAVA_HOME` and let the wrapper pick. (The plugin repos in this
same tree are the opposite case and do want Temurin 21 pinned — do not carry
that habit across.)

## Porting notes for whoever (human or AI) continues this

- **`src/main/java` vs `src/client/java` is not cosmetic.** Stonecraft/Loom
  splits the `main` and `client` source sets: `main`'s compile classpath has
  *no* Minecraft dependency at all, only `client`'s does. Any class that
  imports so much as `net.minecraft.item.ItemStack` must live under
  `src/client/java/...` (same package) or `compileJava` fails with "cannot
  find symbol" on every `net.minecraft.*` type, which looks like a
  mapping/rename problem but isn't. This mod is 100% client-side, so almost
  everything is under `src/client/java`; only genuinely Minecraft-free
  classes (`AutoTrade`, `EasierVillagerTradingConfig`) stay in
  `src/main/java`. When adding a new file, ask "does this import anything
  from `net.minecraft`?" — if yes, it goes in `client`.
- **Stonecraft resource-template variables are exactly**: `${id}`,
  `${name}`, `${group}`, `${description}`, `${version}`, `${minecraftVersion}`,
  `${packVersion}`, `${fabricVersion}`, `${forgeVersion}`, `${neoforgeVersion}`
  — confirmed by reading Stonecraft's own source
  (`gg/meza/stonecraft/configurations/ProcessResources.kt`) and its
  `e2e/testmod` reference resource files, not guessed. There is no
  pre-built version-range or Java-version variable; write ranges literally
  (e.g. `"[${minecraftVersion},)"`, `loaderVersion = "*"`). Any other name
  (e.g. `${mod_id}`, `${minecraft_dependency}`) fails `processResources`
  with `MissingPropertyException`.
- **Start from upstream, not from local `main`.** If gbl ever publishes a
  1.21.x or NeoForge/Forge branch, diff it in before hand-porting further.
- The `BetterGuiMerchant` trade-repeat algorithm (slot-click simulation) has
  proven stable across 1.18.2→1.20.4 Yarn mappings; treat any compile error
  in it on a new version as a real rename, not a logic bug — check the
  decompiled Yarn/Mojmap mappings for that MC version before rewriting logic.
- `ItemStack.areNbtEqual` → `ItemStack.canCombine` was the one rename seen
  crossing 1.19→1.20. Expect more of this pattern (method renamed, same
  semantics) going from 1.20.4 → 1.21.4 → 26.2, especially around item
  components (the 1.20.5 item-stack rewrite) — check compile errors first.
- **26.2 API changes actually hit** (all javap-confirmed against the real
  cached `minecraft-common-deobf-26.2.jar`/`minecraft-clientonly-deobf-26.2.jar`,
  handled via a new `//? if <26.2 { ... } //?} else { ... //?}` split
  alongside the `<1.20.5` one): `net.minecraft.world.inventory.ClickType`
  was removed, replaced 1:1-by-constant-name with
  `net.minecraft.world.inventory.ContainerInput` (do not confuse with
  `ClickAction`, an unrelated 2-constant mouse-button enum that also exists
  in 26.2 and sounds like the replacement but isn't);
  `Screen.hasControlDown()`/`hasShiftDown()` were removed from `Screen`
  entirely and now only exist as instance methods on `KeyEvent`/
  `MouseButtonEvent` (via `InputWithModifiers`) — this mod's mixins have no
  such event object at their injection points, so the 26.2 branch reads the
  raw key state via `InputConstants.isKeyDown(Minecraft.getInstance()
  .getWindow(), InputConstants.KEY_LCONTROL/…)` instead; and
  `Minecraft.setScreen(Screen)` was renamed `setScreenAndShow(Screen)`. See
  `PLAN.md`'s 26.2 per-version-plan entry for the full detail and the exact
  files/methods touched.
- Config is intentionally minimal (one boolean). Do not reintroduce a
  ModMenu/GBfabrictools dependency without re-reading the reasoning above.
