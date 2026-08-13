package de.guntram.mcmod.easiervillagertrading;

//? if fabric {
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 "loaded game" tests: these run against a real, bootstrapped
 * Minecraft and a real Fabric loader rather than mocks, courtesy of
 * fabric-loader-junit (see the dependency comment in build.gradle.kts).
 *
 * <p>{@code EasierVillagerTradingConfigTest} already covers the vendored
 * config class headless, and that is all this mod has that is testable
 * without a game. Everything that actually makes the mod work -
 * {@code BetterGuiMerchant}'s repeat-trade loop and the mixins that install
 * it - is client-screen code, out of reach here. What a loaded game
 * <em>can</em> check is the set of vanilla behaviours that loop is built on,
 * which is exactly where a version bump breaks it:
 *
 * <ul>
 *   <li>the loop's only termination condition, {@code MerchantOffer.isOutOfStock()},
 *       against a real offer whose construction changed shape at 1.20.5
 *       ({@code ItemStack} cost -&gt; {@code ItemCost});</li>
 *   <li>the stack-merge equivalence the inventory scan uses, whose underlying
 *       vanilla call was renamed at 1.20.5 ({@code isSameItemSameTags} -&gt;
 *       {@code isSameItemSameComponents}) - the one place this fork's logic
 *       genuinely differs per version;</li>
 *   <li>the real stack limits {@code canReceiveOutput} does its arithmetic
 *       with;</li>
 *   <li>the loader-supplied config directory the entry point writes to;</li>
 *   <li>and that {@code fabric.mod.json} parses and declares version ranges
 *       this cell actually satisfies.</li>
 * </ul>
 *
 * <p>Fabric cells only: NeoForge's equivalent bootstrap (junit-fml) is only
 * usable from ModDevGradle, not from Architectury Loom - see the junit-fml
 * exclusion comment in build.gradle.kts.
 */
public class LoadedGameTest {

    private static final String MOD_ID = "easiervillagertrading";

    /** Matches the safeguard in {@code BetterGuiMerchant.trade()}. */
    private static final int TRADE_LOOP_SAFEGUARD = 50;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x only, and a real behavioural change rather than test plumbing:
        // an item's data components used to be baked into the Item instance at
        // construction, so Bootstrap alone was enough to read one. In 26.x they
        // are produced by BuiltInRegistries.DATA_COMPONENT_INITIALIZERS from a
        // HolderLookup.Provider - i.e. from loaded registry data - and bound
        // onto each Holder.Reference afterwards. Until that runs, constructing
        // any ItemStack throws "Components not bound yet" from
        // Holder.Reference.components(). The server does this during
        // ReloadableServerResources' load; VanillaRegistries.createLookup() is
        // the equivalent built-in-only provider available without a server.
        // (Guarded at 26.1 because that is where this matrix's 26.x line
        // starts - there is no cell between 1.21.4 and 26.2 to narrow it
        // further, and 1.21.4 demonstrably does not need it.)
        //? if >=26.1 {
        /*net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        *///?}
    }

    @Test
    void gameDataIsActuallyLoaded() {
        // Guard on the harness itself: if the bootstrap above ever silently
        // no-ops, every other assertion in this class becomes vacuous.
        assertNotNull(Items.EMERALD, "Items.EMERALD should be a real loaded game object");
        // The item registry moved from Registry.ITEM to BuiltInRegistries.ITEM
        // in 1.19.3; both expose getKey/keySet identically.
        //? if >=1.19.3 {
        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        //?} else
        /*var itemRegistry = net.minecraft.core.Registry.ITEM;*/
        assertEquals("minecraft:emerald", itemRegistry.getKey(Items.EMERALD).toString());
        assertTrue(itemRegistry.keySet().size() > 500,
                "the real item registry should hold the full vanilla item set");
    }

    @Test
    void modIsDiscoveredByARealFabricLoader() {
        // The processed fabric.mod.json (Stonecraft templating already
        // applied) is on the test classpath, so a real loader discovers this
        // mod exactly as the game would. Malformed or mis-templated metadata
        // fails here instead of at first launch.
        var self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(
                () -> new AssertionError("a real Fabric loader did not discover mod id '" + MOD_ID + "'"));
        assertEquals(MOD_ID, self.getMetadata().getId());
        assertFalse(self.getMetadata().getVersion().getFriendlyString().isBlank(),
                "mod version must survive resource templating");
    }

    @Test
    void declaredDependencyRangesAreSatisfiableInThisCell() {
        // The real drift hazard in a Stonecutter matrix: a cell can compile and
        // package flawlessly while declaring a minecraft range that excludes
        // the very version it was built for, producing a jar that ships and
        // then refuses to load. Here the loader resolves the ranges against the
        // actually-loaded versions, per cell.
        var loader = FabricLoader.getInstance();
        var self = loader.getModContainer(MOD_ID).orElseThrow();
        for (ModDependency dependency : self.getMetadata().getDependencies()) {
            if (dependency.getKind() != ModDependency.Kind.DEPENDS) {
                continue;
            }
            var provider = loader.getModContainer(dependency.getModId());
            assertTrue(provider.isPresent(),
                    "fabric.mod.json requires '" + dependency.getModId() + "' but nothing provides it");
            assertTrue(dependency.matches(provider.get().getMetadata().getVersion()),
                    "fabric.mod.json requires " + dependency + " but this cell loads "
                            + dependency.getModId() + " "
                            + provider.get().getMetadata().getVersion().getFriendlyString());
        }
    }

    @Test
    void repeatTradeLoopTerminatesOnARealOffersStock() {
        // BetterGuiMerchant.trade() repeats `while (!recipe.isOutOfStock() && ...)`.
        // Nothing in the mod decrements anything: it relies entirely on the game
        // marking a real MerchantOffer out of stock once it has been used
        // maxUses times. If that ever stopped holding, the loop would only be
        // stopped by its own safeguard counter - i.e. the mod would spam 50
        // slot-clicks at the server on every trade. Build a real offer and hold
        // the game to it.
        var maxUses = 3;
        var offer = realOffer(maxUses);

        assertFalse(offer.isOutOfStock(), "a freshly built offer should be tradeable");
        assertEquals(maxUses, offer.getMaxUses());

        var iterations = 0;
        while (!offer.isOutOfStock() && iterations < TRADE_LOOP_SAFEGUARD) {
            offer.increaseUses();
            iterations++;
        }

        assertTrue(offer.isOutOfStock(),
                "the game did not lock the offer within the mod's " + TRADE_LOOP_SAFEGUARD + "-click safeguard");
        assertEquals(maxUses, iterations,
                "the loop should stop after exactly maxUses trades, not run to the safeguard");
        assertEquals(maxUses, offer.getUses());

        // Villagers restock; the loop has to become live again when they do.
        offer.resetUses();
        assertFalse(offer.isOutOfStock(), "a restocked offer should be tradeable again");
    }

    @Test
    void offerCostsAndResultAreRealStacksTheInventoryScanCanMatch() {
        // hasEnoughItemsInInventory() reads getCostA()/getCostB() and compares
        // them against inventory stacks with the merge check below; canReceiveOutput()
        // reads getResult(). A 1.20.5 cost is an ItemCost record rather than an
        // ItemStack, and these accessors are what paper over that - so check they
        // still hand back stacks the scan can actually work with. getCostB() is
        // empty here (single-cost offer), which is the case the scan has to
        // tolerate rather than treat as "no emeralds found".
        var offer = realOffer(4);

        assertEquals(Items.EMERALD, offer.getCostA().getItem());
        assertEquals(3, offer.getCostA().getCount());
        assertTrue(offer.getCostB().isEmpty(), "a single-cost offer should report an empty second cost");
        assertEquals(Items.DIAMOND, offer.getResult().getItem());
        assertFalse(offer.getResult().isEmpty());
    }

    @Test
    void stackMergeCheckAgreesWithRealItemStacks() {
        // The inventory scan's notion of "these two stacks are the same thing"
        // is the one piece of this mod's logic that is genuinely
        // version-conditional (isSameItemSameTags -> isSameItemSameComponents at
        // 1.20.5). Both names still have to mean the same thing, or the scan
        // silently stops finding the player's emeralds. Real stacks, real
        // vanilla comparison, per cell.
        assertTrue(areItemStacksMergable(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.EMERALD, 7)),
                "two plain emerald stacks of different sizes must still count as mergable");
        assertFalse(areItemStacksMergable(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)),
                "different items must never count as mergable");

        var fresh = new ItemStack(Items.DIAMOND_PICKAXE);
        var worn = new ItemStack(Items.DIAMOND_PICKAXE);
        assertTrue(fresh.isDamageableItem(), "a real diamond pickaxe should be damageable");
        assertTrue(areItemStacksMergable(fresh, worn), "two undamaged pickaxes must count as mergable");

        worn.setDamageValue(worn.getMaxDamage() / 2);
        assertFalse(areItemStacksMergable(fresh, worn),
                "a damaged tool must not be treated as interchangeable with an undamaged one");
    }

    @Test
    void outputCapacityMathUsesRealStackLimits() {
        // canReceiveOutput() decides whether a trade result fits by comparing
        // counts against getMaxStackSize(). Those limits are game data, and
        // getting them from a real item is the whole point: a stackable result
        // can top up a partial stack, a non-stackable one needs an empty slot.
        var emeralds = new ItemStack(Items.EMERALD);
        var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);

        assertEquals(64, emeralds.getMaxStackSize());
        assertEquals(1, pickaxe.getMaxStackSize());

        // The exact expression from canReceiveOutput(): result of count 1 merging
        // into a nearly-full stack of 63 fits; into a full stack of 64 it does not.
        var nearlyFull = new ItemStack(Items.EMERALD, 63);
        assertTrue(emeralds.getMaxStackSize() >= emeralds.getCount() + nearlyFull.getCount());
        var full = new ItemStack(Items.EMERALD, 64);
        assertFalse(emeralds.getMaxStackSize() >= emeralds.getCount() + full.getCount());
    }

    @Test
    void configRoundTripsThroughTheRealLoaderConfigDir() throws Exception {
        // EasierVillagerTrading.onInitializeClient() does exactly one thing:
        // load the config from FabricLoader.getInstance().getConfigDir(). That
        // call is excluded from coverage because it needs a loader - and here
        // there is one, so the entry point's actual behaviour can be exercised
        // against the directory the real loader hands out.
        Path configDir = FabricLoader.getInstance().getConfigDir();
        assertNotNull(configDir, "a real Fabric loader must supply a config directory");
        Files.createDirectories(configDir);

        File configFile = configDir.resolve("easiervillagertrading.properties").toFile();
        boolean preexisting = configFile.exists();
        byte[] original = preexisting ? Files.readAllBytes(configFile.toPath()) : null;
        try {
            // A fresh instance rather than getInstance(): the singleton is
            // process-wide and would leak this file path into sibling tests.
            var writer = new EasierVillagerTradingConfig();
            writer.load(configDir.toFile());
            assertTrue(configFile.exists(), "load() should have created a default config in the loader's dir");
            writer.setShiftSwapped(true);

            var reader = new EasierVillagerTradingConfig();
            reader.load(configDir.toFile());
            assertTrue(reader.isShiftSwapped(),
                    "the setting must survive a round trip through the real config directory");
        } finally {
            if (preexisting) {
                Files.write(configFile.toPath(), original);
            } else {
                Files.deleteIfExists(configFile.toPath());
            }
        }
    }

    /**
     * A real vanilla trade: 3 emeralds for a diamond. The cost argument stopped
     * being an {@code ItemStack} and became an {@code ItemCost} record at 1.20.5.
     */
    private static net.minecraft.world.item.trading.MerchantOffer realOffer(int maxUses) {
        //? if <1.20.5 {
        /*return new net.minecraft.world.item.trading.MerchantOffer(
                new ItemStack(Items.EMERALD, 3), new ItemStack(Items.DIAMOND), maxUses, 0, 0f);
        *///?} else {
        return new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 3),
                new ItemStack(Items.DIAMOND), maxUses, 0, 0f);
        //?}
    }

    /**
     * Deliberately a byte-for-byte copy of {@code BetterGuiMerchant.areItemStacksMergable},
     * version split and all. That class lives in the {@code client} source set, which Loom's
     * split-environment layout keeps off the test compile classpath, so it cannot be called
     * directly - but the vanilla behaviour it depends on is what is under test here, and
     * this keeps the two definitions visibly in step. If the split below ever needs another
     * branch, both copies need it.
     */
    private static boolean areItemStacksMergable(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem()
                && (!a.isDamageableItem() || a.getDamageValue() == b.getDamageValue())
                //? if <1.20.5 {
                /*&& ItemStack.isSameItemSameTags(a, b);
                *///?} else {
                && ItemStack.isSameItemSameComponents(a, b);
                //?}
    }
}
//?}
