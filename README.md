# EasierVillagerTrading

This is a mod for people who trade with villagers a lot. It does not add any functioniality to villagers, and it doesn't allow you to do anything you can't do in vanilla Minecraft, but it allows you to do the same things with a lot less clicks and mouse movements. Because of this, it can be used with standard, unmodded, servers, You should still check your server owner if they're ok with you using it, but as long as your server has a  "no unfair advantage" policy, they may allow it.

When you open a villager trading window, you'll get a list of all item trades the villager offers next to it. This list includs enchantments in the case of smiths and librarians, and shows you at a glance which trades are enabled and which aren't. Clicking on a trade will execute it once, without you needing to move items to/from the merchant UI.

- **Click** a trade → execute it once.
- **Shift-click** a trade → repeat it until the villager locks it or you run
  out of trade-input items.
- **Ctrl-click** a trade → prepare only (vanilla default behaviour).

## Supported versions and loaders

This is a fork of [gbl/EasierVillagerTrading](https://github.com/guntram/EasierVillagerTrading),
modernized to build for multiple Minecraft versions and loaders from a
single codebase (via [Stonecutter](https://stonecutter.kikugie.dev/) /
[Stonecraft](https://github.com/meza/Stonecraft)):

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.2 | yes | yes | — |
| 1.21.4 | yes | yes | — |
| 1.20.1 | yes | — | yes |
| 1.19.4 | yes | — | yes |
| 1.18.2 | yes | — | yes |

**Quilt**: not built separately — Quilt runs Fabric jars natively via its
Quilted Fabric API compatibility layer, so the Fabric jar for each version
above works on Quilt too.

See `PLAN.md` in the repo for exact build status per cell, and `CLAUDE.md`
for the technical detail on why Forge/NeoForge support lags Fabric (the
mod's core feature relies on mixins into vanilla internals, which are named
differently under Fabric's Yarn mappings vs. Forge/NeoForge's Mojang
mappings).

## Testing

Three tiers, all run by CI (`.github/workflows/build.yml`):

1. **Unit tests + coverage** — JUnit 5 over the config class (the mod's only
   Minecraft-free code), 100% line/branch coverage enforced by JaCoCo.
2. **Loaded-game tests** (`fabric-loader-junit`) — a real bootstrapped
   Minecraft verifies every vanilla contract the repeat-trade loop stands
   on: `MerchantOffer.isOutOfStock()` locking after `maxUses` (the loop's
   sole termination condition), the cost/result accessors across the 1.20.5
   `ItemStack`→`ItemCost` change, real stack-size limits, and that a real
   Fabric loader discovers the mod and can satisfy its dependency ranges.
   Runs on every Fabric cell.
3. **Client gametests** (`fabric-client-gametest-api-v1`, 1.21.4 + 26.2) —
   a **real Minecraft client** walks up to a real wandering trader, opens
   the real trade screen, and executes a real trade through the mod's code
   path. This is the only tier that touches the mixins at all — a mixin
   that fails to apply is a runtime event, not a compile error, so nothing
   else can catch it. CI runs it headless under xvfb.

Details, negative controls, and the per-class exclusion table are in
`PLAN.md`.

To make sure the mod doesn't slow down your minecraft, 
it has been optimized using
 [![JProfiler Logo](https://www.ej-technologies.com/images/product_banners/jprofiler_small.png "Logo")](https://www.ej-technologies.com/products/jprofiler/overview.html).
