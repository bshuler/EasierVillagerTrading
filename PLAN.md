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
| 26.2 | ☐ | ☐ | n/a (NeoForge-era) |
| 1.21.4 | ☐ | ☐ | n/a (NeoForge-era) |
| 1.20.1 | ☐ | n/a (pre-split) | ☐ |
| 1.19.4 | ☐ | n/a | ☐ |
| 1.18.2 | ☐ | n/a | ☐ |

Legend: ☐ not started · 🔶 in progress · ✅ green build · ⛔ blocked (see notes)

This checklist is updated in place as work lands — check the latest commit
for current status, this is not a historical log.

## Why Fabric first, Forge/NeoForge second

Upstream (`gbl/EasierVillagerTrading`) only ever published Fabric (and older
Forge 1.10–1.12, and one Rift 1.13.2) branches — never NeoForge, never modern
Forge. The mod's core feature works by mixin-ing into vanilla merchant-screen
internals, and Fabric (Yarn mappings) vs Forge/NeoForge (Mojang official
mappings) name those internals differently. Porting to Forge/NeoForge is
therefore original work for this fork, not a re-application of an upstream
port. See `CLAUDE.md` § "Why mixins make Forge/NeoForge harder than the
template" for the mechanics.

**Sequencing:** get all 5 MC versions green on Fabric first (direct,
high-confidence port from upstream's own `fabric_1_18`/`fabric_1_19`/
`fabric_1_20` branches — those three are byte-for-byte identical in the mixin
logic). Then spend remaining effort on NeoForge/Forge, version by version,
newest first. If a given Forge/NeoForge cell proves infeasible in the time
available, it is marked ⛔ here with the specific blocker — per the task's own
allowance, **Fabric-only coverage for that version is an acceptable outcome**,
not a failure, as long as it's documented rather than silently dropped.

## Per-version plan

### 1.18.2 — Fabric ☐ · Forge ☐
Base: upstream `fabric_1_18` (targets 1.18.2 exactly). Direct port: drop
GBfabrictools/ModMenu dependency (see CLAUDE.md), adjust package `fabric.mod.json`
entrypoints, wire into Stonecutter cell `1.18.2-fabric`.
Forge: no upstream Forge branch for 1.18.2 exists (upstream's last Forge
branch is `legacy_1_12_2`). Requires re-deriving mixin targets against Mojang
mappings for 1.18.2 from scratch.

### 1.19.4 — Fabric ☐ · Forge ☐
Base: upstream `fabric_1_19` (targets 1.19.3; mixin/logic code identical to
1.18.2 and 1.20 branches — only the `Versionfiles/mcversion-*.properties`
differs). 1.19.3 → 1.19.4 is a low-risk bump (no known API break in the
touched classes).
Forge: same situation as 1.18.2 — no upstream branch, original work.

### 1.20.1 — Fabric ☐ · Forge ☐
Base: upstream `fabric_1_20` (last commit targets 1.20.4; only one rename
vs 1.19 branch: `ItemStack.areNbtEqual` → `canCombine`). 1.20.4 → 1.20.1 is a
*downgrade* within the same minor — expect it to need the *reverse* of that
rename, or possibly neither (need to confirm which of 1.20.1/1.20.4 has which
name — check by compiling).
Forge: 1.20.1 is the last Forge (pre-NeoForge-split) version in the target
matrix — original work, same mapping-translation problem as above.

### 1.21.4 — Fabric ☐ · NeoForge ☐
Base: upstream `fabric_1_20`, hand-advanced. Expect item-stack-component
related renames (the 1.20.5 rewrite of `ItemStack`) between 1.20.4 and 1.21.4;
resolve by reading compiler errors against the Yarn mappings for 1.21.4, not
by guessing.
NeoForge: original work; no upstream branch. This is the loader pairing the
task calls out explicitly for 1.21.4 (matches `critical-orientation`'s own
matrix), so it gets priority over Forge for this version once Fabric is green.

### 26.2 (newest stable) — Fabric ☐ · NeoForge ☐
Original work in both directions — this version is newer than anything
upstream ever targeted. Start from the ported 1.21.4 Fabric source, resolve
whatever Yarn renames the compiler surfaces for 26.2. Confirm the
Stonecraft/Stonecutter/Loom plugin versions used by the house template
actually have Loom/mapping support for 26.2 before investing further; if the
toolchain itself doesn't support it yet, that's a documented ⛔ at the
tooling layer, not a code problem.

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
```

## Log

- Repo surveyed: local `master` = ancient Forge 1.12/FG2.3 tree, unrelated to
  any upstream Fabric work. Upstream has 11 branches, no `main`, `HEAD` →
  `fabric_1_20`. No NeoForge/Forge/post-1.20.4 upstream branch exists.
  `gbfabrictools` (upstream's own library) will not be depended on; replaced
  with a vendored single-boolean config file.
