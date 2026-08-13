# PLAN.md — EasierVillagerTrading Modernization

## Goal

Every target Minecraft version × loader cell **compiles** (`./gradlew
chiseledBuild` green). Running the game client is not required. Newest
version first, then walk backwards, mirroring the house pattern established
in `critical-orientation`.

## Target matrix & status

Newest stable MC version per `https://meta.fabricmc.net/v2/versions/game`
at time of writing: **26.2** (Mojang moved Java Edition to calendar
versioning during 2025/2026; `1.21.x` is no longer latest — verified live,
not from training memory).

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.2 | ✅ full feature | ✅ full feature | n/a (NeoForge-era) |
| 1.21.4 | ✅ full feature | ✅ full feature | n/a (NeoForge-era) |
| 1.20.1 | ✅ full feature | n/a (pre-split) | ✅ full feature |
| 1.19.4 | ✅ full feature | n/a | ✅ full feature |
| 1.18.2 | ✅ full feature | n/a | ✅ full feature |

Legend: ☐ not started · 🔶 in progress · ✅ green build · ⛔ blocked (see notes)

**All 10 cells are ✅ green with full feature parity** (`./gradlew
chiseledBuild` — `BUILD SUCCESSFUL`, 10 jars produced under
`versions/*/build/libs/`) **including tests and coverage on
every cell.** Since Phase 2 wired real JUnit tests into every cell's `test`
task, `chiseledBuild`'s full task graph (`build` → `check` → `test`)
initially surfaced a pre-existing upstream NeoForge-only test-execution bug
on `1.21.4-neoforge`, since fixed via a `junit-fml` test-classpath exclusion
— see "RESOLVED: `chiseledBuild` and `:1.21.4-neoforge:test`" under "Test
coverage (Phase 2)" below. Every cell, on every loader, ships the real
mixin-based trade-repeat feature (`BetterGuiMerchant`, `GuiMerchantMixin`,
`MerchantScreenMixin`) — not a config-only stub. Verified per-cell via
`unzip -l` on the actual built jar (not just "BUILD SUCCESSFUL") — each jar
contains all 6 expected classes (`AutoTrade`, `BetterGuiMerchant`,
`EasierVillagerTrading`, `EasierVillagerTradingConfig`,
`mixins/GuiMerchantMixin`, `mixins/MerchantScreenMixin`) plus the correct
loader metadata (`fabric.mod.json` / `META-INF/mods.toml` /
`META-INF/neoforge.mods.toml`, all with `mixins.easiervillagertrading.json`).
See the Log for the session that closed the Forge/NeoForge feature-parity
gap and extended the matrix to 26.2.

This checklist is updated in place as work lands — check the latest commit
for current status, this is not a historical log.

## Loader-coverage rule (mandatory, updated)

**Every MC version target must build for every loader viable on that
version** — this is now mandatory, not "if feasible":

- **1.20.5 and newer** → Fabric + NeoForge.
- **1.20.4 and older** → Fabric + Forge.
- **Quilt** is not a separate build target: Quilt runs Fabric jars natively
  via its Quilted Fabric API compatibility layer, so Quilt users are already
  covered by the Fabric jar for each version. This is documented (not
  silently assumed) in `CLAUDE.md` and `README.md`.

The matrix below already conforms to this rule (1.21.4 → fabric+neoforge;
1.20.1/1.19.4/1.18.2 → fabric+forge). If any cell turns out to be genuinely
blocked (toolchain gap, no viable mapping, etc.), it is marked ⛔ **here**
with the exact, specific blocker — it is never silently dropped from the
matrix.

## Why Fabric first, Forge/NeoForge second

Upstream (`gbl/EasierVillagerTrading`) only ever published Fabric (and older
Forge 1.10–1.12, and one Rift 1.13.2) branches — never NeoForge, never modern
Forge. The mod's core feature works by mixin-ing into vanilla merchant-screen
internals, and Fabric (Yarn mappings) vs Forge/NeoForge (Mojang official
mappings) name those internals differently. Porting to Forge/NeoForge is
therefore original work for this fork, not a re-application of an upstream
port. See `CLAUDE.md` § "Why mixins make Forge/NeoForge harder than the
template" for the mechanics.

**Sequencing:** got all versions green on Fabric first (direct,
high-confidence port from upstream's own `fabric_1_18`/`fabric_1_19`/
`fabric_1_20` branches — those three are byte-for-byte identical in the mixin
logic). The mixin feature has since been ported to NeoForge/Forge too — see
the Log entry "Forge/NeoForge feature-parity pass" below for how. **All
loaders on all target versions now ship the real feature**, not a stub.

Porting mechanics that made this non-trivial (kept here for whoever touches
this next):
- Forge and NeoForge both reject Loom's `splitEnvironmentSourceSets()`
  (`Using Forge/NeoForge with split jars is not supported!` — neither loader
  ships split client/server jars). So `src/client/java` cannot use the same
  Fabric-only source-set-split mechanism; instead `build.gradle.kts` merges
  it into a hand-created `client` sourceSet whose classpath/configurations/
  jar-inclusion are wired manually (Gradle gives a custom-named sourceSet
  none of that by default — only `"test"` gets automatic `extendsFrom`
  wiring). See the comments in `build.gradle.kts` for the full mechanism and
  the two Gradle pitfalls hit while wiring it (a circular task dependency,
  and a "Property has implicit dependency" validation failure against
  Forge's `generatePackMCMetaJson` task caused by putting the resources dir
  on the client sourceSet's compileClasspath — fixed by scoping to
  `classesDirs` only).
- Forge/NeoForge's `@Mod` annotation is `@Target(ElementType.TYPE)`-only —
  it must sit on the class declaration, not the constructor. A first attempt
  at porting `EasierVillagerTrading.java` put it on the constructor and
  failed with "annotation type not applicable to this kind of declaration"
  the first time that file's Forge/NeoForge branch actually compiled (it
  never had before, since those cells were config-only stubs).
- The mixin *targets* (`MerchantScreen.postButtonClick()`/`shopItem`,
  `MenuScreens.create()`) needed no change between Fabric/Forge/NeoForge —
  all three loaders share the same Mojang-official mapping under this
  Stonecraft/Loom setup (confirmed via javap against the real per-loader
  compile classpaths for every version 1.18.2–1.21.4). Only the build
  wiring above was loader-specific, not the mixin code itself.

## Single merged jar (Forgix) — investigated, not wired in this pass

Bert asked for "one jar that works on all loaders" where possible, via the
**Forgix** Gradle plugin (`PacifistMC/Forgix`), which merges per-loader jars
into a single combined artifact by repackaging classes so each loader only
loads its own package.

**Findings (verified live, August 2026, not from training memory):**
- Forgix is **not dead** — latest release `2.0.0-SNAPSHOT.6` is from
  August 1, 2026 (days before this was written), with a steady cadence of
  snapshot releases through the preceding months. Its own README does
  caveat "this project feels dead" as boilerplate but the release history
  contradicts that literally.
- It supports Fabric/Forge/NeoForge/Quilt merging, matching our loader set.
- It is applied in the **root `build.gradle`**, and its documented usage
  pattern assumes **static, hand-named subprojects** (`:fabric`, `:forge`,
  `:neoforge`) — i.e. the classic Architectury-template layout with one
  fixed subproject per loader. There is **no documented example or evidence
  found of anyone combining Forgix with Stonecutter's per-version-per-loader
  generated subprojects** (`versions:1.21.4-fabric`,
  `versions:1.21.4-neoforge`, etc.), which is what this repo (and the house
  `critical-orientation` template) actually produces — one subproject *per
  MC version × loader*, not one per loader.
- More importantly for **this mod specifically right now**: Forge/NeoForge
  cells do not yet implement the mixin-based trade-repeat feature (see
  above) — they only load config. Merging a fully-featured Fabric jar with
  a stub Forge/NeoForge jar via Forgix today would ship one artifact whose
  behavior silently differs by loader, which is worse than shipping clearly
  separate per-loader jars.

**Decision:** do not wire in Forgix in this pass. Ship per-loader jars from
the single shared codebase (already the case — every jar is built from the
same `src/main/java` tree via Stonecutter, just not merged). Revisit Forgix
once Forge/NeoForge reach mixin-feature parity with Fabric, at which point
(a) the "hide the feature gap" objection above no longer applies, and (b)
it's worth spending the time to prove out Forgix against Stonecutter's
dynamic subproject naming (likely via its generic `merge()`/`inputJar`
override rather than the static `:fabric`/`:forge` shorthand shown in its
docs). This is a revisit, not a rejection.

## Per-version plan

### 1.18.2 — Fabric ✅ · Forge ✅ (full feature parity)
Base: upstream `fabric_1_18` (targets 1.18.2 exactly). Direct port: drop
GBfabrictools/ModMenu dependency (see CLAUDE.md), adjust package `fabric.mod.json`
entrypoints, wire into Stonecutter cell `1.18.2-fabric`. **Green.**
Forge: no upstream Forge branch for 1.18.2 exists (upstream's last Forge
branch is `legacy_1_12_2`), so this loader is original work for the fork.
Mixin targets re-derived against Mojang mappings (same names as Fabric's
Mojmap layer, confirmed via javap — see "Why Fabric first..." above); jar
verified to contain all 6 mod classes plus `META-INF/mods.toml`.

### 1.19.4 — Fabric ✅ · Forge ✅ (full feature parity)
Base: upstream `fabric_1_19` (targets 1.19.3; mixin/logic code identical to
1.18.2 and 1.20 branches — only the `Versionfiles/mcversion-*.properties`
differs). 1.19.3 → 1.19.4 needed no rename in the touched classes. **Green.**
Forge: same situation as 1.18.2 — no upstream branch, original fork work,
now feature-complete and jar-verified.

### 1.20.1 — Fabric ✅ · Forge ✅ (full feature parity)
Base: upstream `fabric_1_20` (last commit targets 1.20.4; only one rename
vs 1.19 branch: `ItemStack.areNbtEqual` → `canCombine`). Compiled clean
against 1.20.1 with no further rename needed beyond that one. **Green.**
Forge: 1.20.1 is the last Forge (pre-NeoForge-split) version in the target
matrix — feature-complete and jar-verified. This is also the cell that
surfaced the Forge-specific `generatePackMCMetaJson`/client-sourceSet
Gradle validation failure (see build.gradle.kts comments); fixed there and
confirmed it does not recur on any other Forge cell.

### 1.21.4 — Fabric ✅ · NeoForge ✅ (full feature parity)
Base: upstream `fabric_1_20`, hand-advanced. The only extra fix needed to
get this cell (and every other cell) compiling was unrelated to Yarn
renames: (1) moving every Minecraft-touching class from `src/main/java` to
`src/client/java` (Stonecraft splits the `main`/`client` source sets; `main`
has no Minecraft classpath at all), and (2) fixing the resource-template
placeholders (`${mod_id}` etc. don't exist in Stonecraft — see Log). No
item-component-rewrite renames were actually hit in this class set. **Green.**
NeoForge: original work; no upstream branch. Now feature-complete
(mixin-based trade-repeat), jar-verified; confirmed NeoForge does **not**
hit the `generatePackMCMetaJson` validation issue Forge cells hit (NeoForge
registers no such task).

### 26.2 (newest stable) — Fabric ✅ · NeoForge ✅ (full feature parity)
No longer blocked — the house template `critical-orientation` proved the
Gradle 9.7.0 + Stonecraft/Loom toolchain builds 26.2-fabric/26.2-neoforge
green, so the earlier "unverified toolchain" blocker no longer applies.
Porting the mixin feature itself to 26.2 required real, javap-confirmed
upstream API changes beyond the earlier 1.20.5 item-component rewrite
(all version-conditioned with a fresh `//? if <26.2 { ... } //?} else {
... //?}` split alongside the existing `<1.20.5` one):
- `net.minecraft.world.inventory.ClickType` was removed; its 4th-parameter
  role in `AbstractContainerScreen.slotClicked(Slot, int, int, X)` is now
  played by `net.minecraft.world.inventory.ContainerInput` — a same-named-
  constants enum (`PICKUP`/`QUICK_MOVE`/`SWAP`/`CLONE`/`THROW`/`PICKUP_ALL`,
  plus a new `QUICK_CRAFT`), i.e. a straight rename for this mod's purposes,
  not a semantic redesign. (Note: `net.minecraft.world.inventory.ClickAction`
  also exists in 26.2 but is an unrelated, much smaller enum — only
  `PRIMARY`/`SECONDARY`, a mouse-button concept — and is *not* the
  replacement for `ClickType`; easy to confuse by name.)
- `Screen.hasControlDown()`/`hasShiftDown()` (static utility methods through
  1.21.4) no longer exist on `Screen` — confirmed via javap returning no
  such methods. The same behavior moved onto a per-input-event instance
  method (`net.minecraft.client.input.InputWithModifiers`, implemented by
  the new `KeyEvent`/`MouseButtonEvent` record types). This mod's mixin
  injection points (`postButtonClick` RETURN, and `BetterGuiMerchant.trade()`)
  have no such event object available, so the 26.2 branch reads the raw key
  state directly instead, via `com.mojang.blaze3d.platform.InputConstants
  .isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LCONTROL
  /KEY_RCONTROL/KEY_LSHIFT/KEY_RSHIFT)` — the same primitive the old
  `Screen.hasControlDown()`/`hasShiftDown()` themselves wrapped.
- `Minecraft.setScreen(Screen)` was renamed `setScreenAndShow(Screen)`
  (confirmed via javap: only `setScreenAndShow` exists on the real 26.2
  `Minecraft.class`).
- The mixin *targets* did **not** need to change: `MerchantScreen` still has
  the same private `shopItem` field, private `postButtonClick()` method, and
  `(MerchantMenu, Inventory, Component)` constructor in 26.2 (confirmed via
  javap against the real cached 26.2 deobfuscated jars).
All findings were ground-truthed with `javap` against the real cached
`minecraft-common-deobf-26.2.jar`/`minecraft-clientonly-deobf-26.2.jar`
(Gradle's Loom cache), never assumed from training-data memory. Both
`26.2-fabric` and `26.2-neoforge` build green and are jar-verified to
contain all 6 mod classes plus correct loader metadata.

## Test coverage (Phase 2)

**This mod is almost entirely mixin/GUI/loader-entry-point code.** A honest
hunt across all 6 shared-source classes (`AutoTrade`, `BetterGuiMerchant`,
`EasierVillagerTrading`, `EasierVillagerTradingConfig`,
`mixins/GuiMerchantMixin`, `mixins/MerchantScreenMixin`) found exactly **one**
genuinely headless-testable class. JUnit 5 + JaCoCo were wired into
`build.gradle.kts` (plugin `jacoco`, `tasks.test { useJUnitPlatform();
finalizedBy(jacocoTestReport) }`, a shared `jacocoExcludes` file-pattern list
applied to both `jacocoTestReport` and `jacocoTestCoverageVerification`
`classDirectories`, a `LINE` `COVEREDRATIO` `1.00` violation rule, and
`tasks.check { dependsOn(jacocoTestCoverageVerification) }`) — the same
wiring pattern as `critical-orientation`/`FlightHud`/`critical-flight-details`.
Tests run against the **active Stonecutter project only** (`1.21.4-fabric`,
which matches this repo's `vcsVersion`); this is a client mod, there is no
server-side matrix, and the pure logic under test has no version-conditional
branches (no `//? if` markers in it at all), so testing every cell would
duplicate effort for zero additional signal.

### Coverage scope and result

| Class | In scope? | Reason |
|---|---|---|
| `EasierVillagerTradingConfig` | **Yes** — 100% line (30/30), 100% branch (6/6) | Plain `java.util.Properties` file I/O; zero Minecraft imports; fully instantiable and exercisable in a plain JVM. |
| `AutoTrade` | Left unexcluded (harmless) | Bodiless interface — one abstract method declaration, zero bytecode instructions. JaCoCo's report lists the class but assigns it no counters at all, so it can neither pass nor fail the ratio; excluding it would be pure noise. |
| `BetterGuiMerchant` | **Excluded**, documented here | Extends the real `net.minecraft.client.gui.screens.inventory.MerchantScreen` and reads/writes live `MerchantMenu` slot state (`menu.getSlot(i)`, `menu.slots.size()`, `this.slotClicked(...)`). Instantiating it at all requires a running Minecraft client (a `Screen` superclass constructor call chain, `Font`/`Component` rendering state, a real `MerchantMenu`); there is no MockBukkit-equivalent mock client for Fabric/Forge/NeoForge GUI screens. Genuinely untestable headless. |
| `EasierVillagerTrading` | **Excluded**, documented here | Mod entry point. Fabric branch calls `FabricLoader.getInstance()`; Forge/NeoForge branches take an `IEventBus`/`FMLJavaModLoadingContext` constructor argument and register `FMLClientSetupEvent` listeners. All three loader branches require a live loader environment to construct or invoke. |
| `mixins/GuiMerchantMixin` | **Excluded**, documented here | A Mixin `@Inject` target class. It is never instantiated or called directly — the Mixin annotation processor rewrites `MenuScreens` bytecode at game-launch time to jump into this method. Calling it outside that transformed runtime is meaningless (it dereferences `client.player.getInventory()` etc. against a real `Minecraft` client instance). |
| `mixins/MerchantScreenMixin` | **Excluded**, documented here | Same as above: an abstract Mixin class that `extends AbstractContainerScreen<MerchantMenu>` and is spliced into `MerchantScreen` by the Mixin transformer at class-load time, not something a plain JUnit test can instantiate. |

**Result: 100% line coverage of the entire genuinely-testable surface** (one
class, `EasierVillagerTradingConfig`) — 30/30 lines, 6/6 branches, enforced
by `jacocoTestCoverageVerification` (which `check` depends on). Confirmed via
the actual JaCoCo XML report that the analyzed bundle is non-trivial (per
GOTCHA (t) in the phase-2 brief): the Ant `jacocoReport` task logs `Writing
bundle '1.21.4-fabric' with 1 classes` (`AutoTrade` carries no counters at
all, so JaCoCo's own bundle-writer doesn't count it as an analyzed class —
this is the expected shape for a bodiless interface, not a malformed-include
false green) and the report's `<counter type="CLASS" .../>` at the package
and bundle level reads `covered="1"`, matching.

8 real behavioral tests (`EasierVillagerTradingConfigTest`), each asserting
actual outcomes (file contents, returned booleans, singleton identity), none
are "constructs without throwing" filler:
- `getInstance()` singleton identity across two calls.
- Default `isShiftSwapped()` on a fresh instance.
- `save()` before `load()` was ever called is a no-op (the
  `configFile == null` guard) and does not throw, and writes nothing.
- `load()` on a missing config file creates it with `swapShiftBehavior=false`
  and that value round-trips.
- `load()` on a pre-seeded `swapShiftBehavior=true` file reads `true`.
- `load()` on a file with no swap key defaults to `false`.
- `load()` when the config **path is a directory** (forcing both
  `FileInputStream`/`FileOutputStream` open failures deterministically,
  independent of filesystem permissions) falls back to the default and does
  not throw; a subsequent `setShiftSwapped()`/`save()` against the same
  unwritable path also degrades gracefully (updates in-memory state, doesn't
  crash) — this exercises both try/catch `IOException` branches per GOTCHA
  (w) in the phase-2 brief (each try block's *success* path is also covered
  separately by the tests above, since a test that only hits the catch path
  never covers the try's normal continuation).
- `setShiftSwapped()` updates the in-memory value, persists it, and a second
  instance loading the same directory observes the persisted value.

No bugs were found in `EasierVillagerTradingConfig` while writing these
tests — the class already behaved as documented (graceful degradation on
I/O failure, correct default, correct persistence round-trip).

Run tests + coverage for the active cell:

```bash
./gradlew ":1.21.4-fabric:test" ":1.21.4-fabric:jacocoTestReport" ":1.21.4-fabric:jacocoTestCoverageVerification"
# or, equivalently (check depends on the verification task):
./gradlew ":1.21.4-fabric:check"
```

HTML report: `versions/1.21.4-fabric/build/reports/jacoco/test/html/index.html`.

### RESOLVED: `chiseledBuild` and `:1.21.4-neoforge:test` (`junit-fml` / `mainargs.txt`)

Wiring real JUnit tests into `build.gradle.kts` means every cell's `test`
task now actually spins up a Gradle Test Executor (before this pass, `test`
was `NO_SOURCE` on every cell and never ran a JVM for it, so this was
latent/invisible). `:1.21.4-neoforge:test` initially failed with
`java.nio.file.NoSuchFileException: mainargs.txt` — a pre-existing upstream
NeoForge/FML bug: NeoForge's transitive
`net.neoforged.fancymodloader:junit-fml` artifact for MC 1.21.4-era releases
looks up a run-config file (`mainargs.txt`) via a relative path that doesn't
resolve under Gradle's test-worker working directory. Forcing the
upstream-fixed junit-fml 10.0+ isn't viable either (it needs a newer FML
core API surface than this NeoForge release ships).

**The fix** (proven across the sibling repos — simple-utilities-mod,
ToroHealth, critical-orientation): `junit-fml` exists to bootstrap FML for
*gametests*, and this repo's tests are plain pure-logic JUnit tests that
need none of that, so its auto-registered `LauncherSessionListener` (the
code performing the failing lookup) is excluded from `testRuntimeClasspath`
on NeoForge cells in `build.gradle.kts`. With the exclusion in place,
`./gradlew chiseledBuild` runs `test`/`check` fully green on all 10 cells.
(`26.2-neoforge`'s newer FML never carried the buggy `junit-fml` and was
green throughout.)

**What this exclusion costs (recorded 2026-08-13).** `junit-fml` is precisely
NeoForge's own *loaded-test bootstrap* — it is what stands FML up so a test can
run against a real, loaded game. Excluding it is the right call here, but it is
a Loom-specific workaround, not a universal fix. NeoForge's supported
loaded-test path (`neoForge { unitTest { enable(); testedMod = ... } }`, the
`net.neoforged:testframework` artifact,
`@ExtendWith(EphemeralTestServerProvider.class)` injecting a live
`MinecraftServer`, and `gradlew runGameTestServer`) is **ModDevGradle-only**,
and this repo builds on Architectury Loom via Stonecraft, so that path is
unavailable here regardless. The exclusion therefore costs nothing today — but
if a cell is ever migrated to ModDevGradle, revisit it before writing any
loaded NeoForge test, because it would silently disable the very bootstrap
such a test depends on.

## Folia

Folia n/a — client mod. This is a 100% client-side Fabric/Forge/NeoForge
mod (no server component at all — see CLAUDE.md "What this mod does"); Folia
is a Paper-server fork with no client-mod analog, so the Folia
compatibility pass in the phase-2 brief does not apply here.

## Non-goals / explicit exclusions

- Publishing to Modrinth/CurseForge — not part of this pass (binding repo rule).
- Running the game client to verify in-game behavior — compile-green is the bar.
- Keeping the `GuiActionConfirmDebug` / `ClickWindowC2SPacketDebugMixin`
  debug mixins active — upstream itself lists them under `"notused"` in
  `mixins.easiervillagertrading.json`; carried over as dead/unused code at
  most, not wired in.
- Reintroducing GBfabrictools/ModMenu — see CLAUDE.md.

## Build/verify commands

```bash
./gradlew chiseledBuild          # everything
./gradlew :1.20.1-fabric:build   # one cell

# Tests + coverage (active project only, see "Test coverage (Phase 2)" above)
./gradlew ":1.21.4-fabric:check"
```

## Log

- Repo surveyed: local `master` = ancient Forge 1.12/FG2.3 tree, unrelated to
  any upstream Fabric work. Upstream has 11 branches, no `main`, `HEAD` →
  `fabric_1_20`. No NeoForge/Forge/post-1.20.4 upstream branch exists.
  `gbfabrictools` (upstream's own library) will not be depended on; replaced
  with a vendored single-boolean config file.
- Scaffold in place: `settings.gradle.kts`/`stonecutter.gradle.kts`/
  `build.gradle.kts`/`gradle.properties` mirror `critical-orientation`.
  Shared source tree ported from `fabric_1_20`
  (`EasierVillagerTrading.java`, `EasierVillagerTradingConfig.java`,
  `BetterGuiMerchant.java`, `AutoTrade.java`, `mixins/GuiMerchantMixin.java`,
  `mixins/MerchantScreenMixin.java`). The three Yarn-mapping-dependent files
  (`BetterGuiMerchant`, both mixins) are wrapped in Stonecutter
  `//? if fabric { ... } //?}` guards so Forge/NeoForge cells compile to an
  empty (package-only) file instead of failing on missing Yarn class names —
  this is the mechanism, not yet the feature port; Forge/NeoForge mixin
  porting is still open (see per-version plan and loader-coverage rule
  above).
- Updated requirement received mid-task: loader coverage across the matrix
  is now mandatory, and a single merged all-loader jar (Forgix) was
  requested where feasible. Investigated Forgix live (see "Single merged
  jar" section above): actively released as of Aug 1 2026, but its
  documented usage assumes static per-loader subprojects, not Stonecutter's
  per-version-per-loader generated ones, and merging today would hide the
  Fabric-vs-Forge/NeoForge feature gap — decided not to wire it in this
  pass, revisit once loader parity is reached. No resource files
  (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`,
  `mixins.easiervillagertrading.json`), `LICENSE`, or first Gradle build
  have landed yet — next steps.
- First real build attempt (`./gradlew :1.21.4-fabric:build`) surfaced three
  real bugs, found and fixed by reading actual compiler/Gradle output rather
  than guessing:
  1. Gradle/Stonecutter scaffold was stale (Gradle 8.11.1 wrapper, old flat
     Stonecutter DSL, missing `versions/dependencies/*.properties` pins) —
     fixed by re-copying the wrapper and rewriting `settings.gradle.kts` to
     match `critical-orientation`'s current (Gradle 9 / Stonecutter 0.9.+ /
     Stonecraft 1.12.+) structure, and copying its dependency-pin files for
     the same four MC versions.
  2. **Real compile failure, not a false alarm**: `compileJava` for
     `:1.21.4-fabric` failed with "cannot find symbol" on *every* `net.minecraft.*`
     type (`ItemStack`, `Text`, `PlayerInventory`, `MerchantScreenHandler`,
     `MinecraftClient`, etc.) — not a mapping rename, the entire Minecraft
     dependency was absent from the `main` source set's classpath. Root
     cause: Stonecraft/Loom split the `main` and `client` source sets (same
     convention `critical-orientation` uses — its `OrientationClient.java`/
     `OrientationKeyBind.java` live in `src/client/java`, only the pure-math
     `OrientationCommon.java` stays in `src/main/java`), and every EVT class
     that touches Minecraft was sitting in `src/main/java`, which only gets
     the loader-agnostic (non-Minecraft) classpath. Fixed by moving
     `EasierVillagerTrading.java`, `BetterGuiMerchant.java`, and both
     `mixins/*.java` to `src/client/java/...` (same package, same
     Stonecutter guards) since this mod is 100% client-side. Only
     `AutoTrade.java` (interface, no Minecraft import) and
     `EasierVillagerTradingConfig.java` (plain `java.io.File`/`Properties`,
     no Minecraft import) legitimately stay in `src/main/java`.
  3. `processResources` failed with `MissingPropertyException: mod_id` —
     the four resource files (copied from a stale/incorrect assumption about
     Stonecraft's template variable names, `${mod_id}`/`${mod_version}`/
     `${mod_name}`/`${mod_description}`/`${minecraft_dependency}`/
     `${java_version}`/`${loader_version_range}`/`${forge_version_range}`/
     `${neoforge_version_range}`/`${minecraft_version_range}`) do not match
     Stonecraft's actual expansion map. Confirmed by reading Stonecraft's own
     source (`gg/meza/stonecraft/configurations/ProcessResources.kt`,
     cloned live from `github.com/meza/Stonecraft`) — the only keys it
     defines are `id`, `name`, `group`, `description`, `version`,
     `minecraftVersion`, `packVersion`, `fabricVersion`, `forgeVersion`,
     `neoforgeVersion` (plus anything added via `modSettings.variableReplacements`
     in `build.gradle.kts`, unused here) — and by reading Stonecraft's own
     `e2e/testmod` reference `fabric.mod.json`/`mods.toml`/
     `neoforge.mods.toml`, which use `${id}`/`${version}`/`${name}`/
     `${description}`/`${minecraftVersion}` and a literal `"*"` for loader
     version ranges and `"[${minecraftVersion},)"` for the Minecraft version
     range — there is no built-in range-formatting or Java-version variable
     at all. Rewrote all three resource files to match this exactly; the
     `critical-orientation` template's own copies of these same files
     (read directly) turn out to have the **same wrong** `${mod_id}`-style
     placeholders, so that template build is presumably hitting or will hit
     this identical error — worth flagging back, not something to silently
     copy further.
  After both fixes, `./gradlew :1.21.4-fabric:build --no-daemon` → **BUILD
  SUCCESSFUL**. `chiseledBuild --continue` across the full matrix started
  next; results land in the status table above as they complete.
- `./gradlew chiseledBuild --no-daemon --continue` → **BUILD SUCCESSFUL in
  1m 30s, 66 actionable tasks, 0 FAILED**, across all 8 mandatory cells
  (1.21.4-fabric, 1.21.4-neoforge, 1.20.1-fabric, 1.20.1-forge, 1.19.4-fabric,
  1.19.4-forge, 1.18.2-fabric, 1.18.2-forge). 8 jars produced under
  `versions/*/build/libs/`. No further Yarn-rename or mapping issues surfaced
  once the two bugs above were fixed — the only compile-blocking problems in
  this whole pass were structural (source-set split, template variable
  names), not version-specific renames. Forge/NeoForge cells are stub-only
  (config load, no mixin feature) by the `//? if fabric` guard mechanism —
  matrix-wide compile-green is met; feature parity is deliberately left as
  the next unit of work (see per-version plan above), not silently treated
  as "done."
- Side note for whoever picks up `critical-orientation` next: its own
  `fabric.mod.json`/`mods.toml`/`neoforge.mods.toml` (read directly from
  that repo while building this one) use the same wrong `${mod_id}`-style
  placeholders this repo had — it will hit the identical
  `MissingPropertyException: mod_id` the first time its `processResources`
  actually runs, unless already fixed independently.
- **Forge/NeoForge feature-parity pass, and 26.2 matrix extension.** Two
  gaps closed in this session:
  1. Forge and NeoForge cells were still config-only stubs (the mixin
     feature was Fabric-only). Ported it for real. The real work was almost
     entirely Gradle plumbing, not mixin-target renames (mixin targets are
     identical across all three loaders, per the javap evidence already in
     `BetterGuiMerchant.java`'s header comment). Root problem: Loom's
     `splitEnvironmentSourceSets()` throws
     `UnsupportedOperationException` on both Forge and NeoForge (`"Using
     Forge/NeoForge with split jars is not supported!"`), so `src/client/java`
     needed a different wiring path for `mod.isForgeLike` cells. First
     attempt (bolting an extra `srcDir` onto the existing `main` sourceSet)
     produced a **green build that silently shipped an empty jar** — exactly
     the failure mode this task was warned about — because Stonecutter's
     per-cell code generation is keyed off the literal sourceSet name
     (`stonecutterGenerate<Name>` reads `src/<name>/java`/writes
     `build/generated/stonecutter/<name>/...`); it has no generic "scan every
     srcDir on every sourceSet" behavior, so a same-named `client` folder
     tacked onto `main` is invisible to it. Fixed by creating a genuine
     `sourceSets.create("client")` and hand-wiring its
     classpath/configurations (`clientImplementation` etc. don't
     automatically `extendsFrom` the main configs — only `"test"` gets that
     for free) and jar inclusion (`tasks.named<Jar>("jar") { from(...) }`,
     deliberately *not* also adding `classes.dependsOn(clientClasses)`,
     which creates a real Gradle task cycle since `compileClientJava`'s
     classpath already makes Gradle infer `compileClientJava -> classes` on
     its own). Also hit and fixed the "Property has implicit dependency"
     Gradle validation failure this task's gotcha list called out in
     advance: giving the client sourceSet's compileClasspath the *full*
     main sourceSet output (which bundles the resources dir) collided with
     Forge's `generatePackMCMetaJson` task writing `pack.mcmeta` into that
     same directory — fixed by scoping the client compileClasspath to
     `classesDirs` only. Separately found and fixed a real (previously
     uncaught) bug in `EasierVillagerTrading.java`: `@Mod(MODID)` was on the
     constructor, but Forge/NeoForge's `@Mod` is `@Target(ElementType.TYPE)`
     — moved to the class declaration. Confirmed via `unzip -l` on every
     built jar (not just "BUILD SUCCESSFUL") that all 6 mod classes and
     correct loader metadata now ship on every Forge/NeoForge cell.
  2. Extended the matrix to **26.2** (both Fabric and NeoForge), following
     `critical-orientation` proving the toolchain itself works at that
     version. Porting the actual mixin code required three real,
     javap-confirmed upstream API changes beyond the existing
     `isSameItemSameTags`→`isSameItemSameComponents` split — see the 26.2
     entry in "Per-version plan" above for the exact renames
     (`ClickType`→`ContainerInput`, `Screen.hasControlDown/hasShiftDown`
     relocated off `Screen` entirely, `Minecraft.setScreen`→
     `setScreenAndShow`). All three handled with a new `//? if <26.2 { ...
     } //?} else { ... //?}` conditional in `BetterGuiMerchant.java`,
     `MerchantScreenMixin.java`, and `GuiMerchantMixin.java`.
  3. Re-ran `./gradlew chiseledBuild` across the full 10-cell matrix
     (1.18.2/1.19.4/1.20.1 × fabric+forge, 1.21.4/26.2 × fabric+neoforge)
     after all fixes — **BUILD SUCCESSFUL**, and every single cell's jar was
     individually verified via `unzip -l` to contain all 6 expected classes
     plus correct per-loader metadata. No regressions in the previously-
     green Fabric cells.
- **Phase 2 (test coverage + Folia)**: added JUnit 5 + JaCoCo to
  `build.gradle.kts` (`jacoco` plugin, `finalizedBy(jacocoTestReport)`, a
  shared `jacocoExcludes` list, `LINE` `COVEREDRATIO` `1.00` verification
  rule, `check` depends on it), mirroring `critical-orientation`'s wiring.
  Hunted the shared/client source tree for genuinely headless-testable
  logic: of 6 classes, only `EasierVillagerTradingConfig` (vendored
  properties-file persistence, zero Minecraft imports) qualifies — every
  other class either extends a live Minecraft `Screen`/`MerchantMenu`, is a
  loader entry point requiring a real `FabricLoader`/`IEventBus`, or is a
  Mixin class the annotation processor splices into game bytecode at
  launch time (see "Test coverage (Phase 2)" section above for the
  per-class table). Wrote 8 real behavioral tests
  (`EasierVillagerTradingConfigTest`) covering the singleton, both
  `load()`/`save()` try-success paths and both their `IOException` catch
  paths (directory-as-file trick, filesystem-permission-independent), the
  missing/present/keyless config-file cases, and the persistence
  round-trip. Ran against the active project only (`1.21.4-fabric`, matches
  `vcsVersion`) per the phase-2 brief's mod-specific rule against running
  tests across the full matrix. Result: **100% line coverage (30/30) and
  100% branch coverage (6/6)** of the one in-scope class, enforced by the
  build; verified the JaCoCo bundle actually analyzed a non-trivial class
  count (`Writing bundle '1.21.4-fabric' with 1 classes` in the Ant task's
  own log, plus `<counter type="CLASS" .../>` `covered="1"` in the XML
  report) rather than trusting a bare "BUILD SUCCESSFUL". No bugs found in
  the tested class — it already behaved correctly. Folia: n/a, this is a
  100% client-side mod with no server component at all; documented as a
  one-line verdict per the phase-2 brief's mod-specific rule. Final
  regression: re-ran `./gradlew chiseledBuild` across all 10 cells after
  the build-script change — **BUILD SUCCESSFUL**, no regressions. Active
  Stonecutter project left at `1.21.4-fabric`, matching `vcsVersion`
  (unchanged throughout this pass — never needed to switch cells).

## Coverage in context (measured 2026-08-13)

Read from the JaCoCo XML report, not from whether the gate passes:

- **Analysed surface:** 2 of 2 compiled classes (100%).
- **Line coverage of that surface:** 100.0% (30 lines analysed).
- Classes outside that surface are excluded by the documented exclusion list. They
  are not covered by any test and are not runtime-verified.
  Measured from `EasierVillagerTrading/versions/1.18.2-fabric/build/reports/jacoco/test/jacocoTestReport.xml`.

A passing `check` means "no regression inside the analysed surface" — it does not
mean the whole codebase is tested to that percentage.
