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
```

Only JDK available in this environment is **Temurin 21**
(`/Library/Java/JavaVirtualMachines/temurin-21.jdk`). Older MC versions need
older Java at *runtime* (1.18.2/1.19.4 → Java 17) but Loom/NeoGradle/ForgeGradle
toolchains handle that via Gradle's Java toolchain auto-provisioning
(foojay-resolver, downloads into `~/.gradle/jdks`) — never install a system
JDK, never touch Homebrew for this.

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
- Config is intentionally minimal (one boolean). Do not reintroduce a
  ModMenu/GBfabrictools dependency without re-reading the reasoning above.
