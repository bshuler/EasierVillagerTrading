package de.guntram.mcmod.easiervillagertrading.gametest;

import java.lang.reflect.Method;
import java.util.Locale;

import de.guntram.mcmod.easiervillagertrading.AutoTrade;
import de.guntram.mcmod.easiervillagertrading.BetterGuiMerchant;
import de.guntram.mcmod.easiervillagertrading.EasierVillagerTradingConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Tier 3: this mod running inside a <strong>real Minecraft client</strong> --
 * real window, real GL context, real world, a real wandering trader in the
 * crosshair, a real use-key press, a real merchant menu, a real trade.
 *
 * <p>It exists because of a hole that is unusually wide in this particular
 * repo. This mod is, almost in its entirety, two Mixins. Everything the user
 * actually experiences happens because
 * {@code mixins.easiervillagertrading.json} manages to weave
 * {@code GuiMerchantMixin} into {@code MenuScreens} and
 * {@code MerchantScreenMixin} into {@code MerchantScreen} at class-load time.
 * <strong>Mixin application is a runtime event, and its failure is invisible
 * to every other tier.</strong> Rename an injection target, move an injection
 * point, mistype a descriptor, and the build stays green, the jar still
 * packages, Tier 1's loaded-game tests still pass -- and the mod silently does
 * nothing (or, given this config declares {@code "required": true} with
 * {@code "injectors": {"defaultRequire": 1}}, takes the whole client down on
 * the first merchant screen).
 *
 * <p>That risk is not theoretical here. This fork carries the mod to MC 26.2,
 * a version upstream never targeted, across three separate 26.2 API breaks in
 * the mixin sources alone ({@code ClickType} to {@code ContainerInput},
 * {@code Screen.hasShiftDown}/{@code hasControlDown} removed,
 * {@code Minecraft.setScreen} to {@code setScreenAndShow}). Before this test
 * existed, nothing in the repo had ever opened a villager trade screen on any
 * version.
 *
 * <h2>What is actually asserted</h2>
 *
 * <ol>
 *   <li><strong>{@code MerchantScreenMixin} applied.</strong> Reflection over
 *       {@code MerchantScreen}'s declared methods for the injected handler.
 *       See {@link #assertMerchantScreenMixinApplied()} for why this is the
 *       only available evidence for that one mixin.</li>
 *   <li><strong>{@code GuiMerchantMixin} applied and functioning.</strong> The
 *       flagship. A wandering trader is staged in front of the player and the
 *       real use key is pressed; the server opens a merchant menu and sends
 *       {@code ClientboundOpenScreenPacket}; the client's
 *       {@code ClientPacketListener.handleOpenScreen} calls
 *       {@code MenuScreens.create}; the mixin cancels it and substitutes
 *       {@code BetterGuiMerchant}. The assertion is
 *       {@code waitForScreen(BetterGuiMerchant.class)} -- the whole chain, end
 *       to end, through the real packet path rather than by calling
 *       {@code MenuScreens.create} directly.</li>
 *   <li><strong>The repeat-trade algorithm actually trades.</strong>
 *       {@code BetterGuiMerchant.trade} is invoked on a real, open, live
 *       merchant menu, and the player's inventory is read back afterwards. See
 *       {@link #assertOneTradeExecutes} -- this is the only coverage the
 *       {@code ClickType}/{@code ContainerInput} split and the whole
 *       slot-click protocol have ever had.</li>
 * </ol>
 *
 * <h2>What is deliberately NOT asserted, and why</h2>
 *
 * <p><strong>The trade button's own click is not driven.</strong>
 * {@code MerchantScreenMixin} injects at the RETURN of
 * {@code MerchantScreen.postButtonClick}, which vanilla calls from the
 * {@code TradeOfferButton} press handler. That method is {@code private} in
 * vanilla and the buttons carry no text, so neither a direct call nor
 * {@code ClientGameTestContext.clickScreenButton} can reach them; synthesising
 * a mouse click would mean hardcoding widget geometry that differs between
 * 1.21.4 and 26.2 and re-deriving the GUI-scale-to-window-pixel conversion,
 * which is a brittle assertion about layout dressed up as a behavioural one.
 * So this test calls {@code AutoTrade.trade} directly, exactly as the mixin
 * does one line later, and the mixin's own two {@code QUICK_MOVE} slot clicks
 * and its ctrl-key bypass remain uncovered. The reflection check in step 1 is
 * therefore load-bearing: it is what distinguishes "the injector resolved" from
 * "the injector silently found no target". Anyone tightening this tier should
 * start here.
 *
 * <h2>Portability</h2>
 *
 * <p>Stonecutter processes this source set, and this file carries exactly one
 * version branch: {@link #currentScreen(Minecraft)}, because
 * {@code Minecraft.screen} (a public field through 1.21.4) was removed in 26.2
 * in favour of {@code Minecraft.gui.screen()}. Everything else was checked with
 * javap against both cells' real jars first and came back identical --
 * {@code Options.keyUse}, {@code Minecraft.crosshairPickEntity},
 * {@code AbstractContainerMenu.slots}, {@code MerchantMenu.getOffers},
 * {@code MerchantOffer.getCostA/getCostB/getResult/isOutOfStock},
 * {@code BuiltInRegistries.ITEM}, and the packages of every type imported
 * above. The client-gametest API surface used here is likewise present in both
 * v4.1.1 (1.21.4) and v6.0.0 (26.2); {@code TestInput.lookAt}, which exists
 * only in v6, is avoided in favour of a {@code tp} command.
 */
public class EasierVillagerTradingClientGameTest implements FabricClientGameTest {
    /** Ticks to let the world, and any server-to-client sync, settle. */
    private static final int SETTLE_TICKS = 20;

    /**
     * Ticks allowed for a trade to round-trip.
     *
     * <p>Longer than {@link #SETTLE_TICKS} on purpose. {@code trade} issues a
     * burst of {@code slotClicked} calls, each of which applies a local
     * prediction and sends a packet; the server validates the sequence and may
     * answer with a full container resync. The assertion reads the client's
     * view, so it has to be taken after that resync has had time to land,
     * otherwise a pass could be reading nothing but optimistic client-side
     * prediction.
     */
    private static final int TRADE_SETTLE_TICKS = 40;

    private static final String MOD_ID = "easiervillagertrading";

    /**
     * Staging: freeze the world, empty the player, and put one wandering
     * trader in the crosshair.
     *
     * <p>Each command that needs the player's own position is wrapped in
     * {@code execute as @p at @s}, because {@code runCommand} dispatches from
     * the server's command source, whose position is the world spawn.
     *
     * <p>Notes on the three non-obvious ones:
     *
     * <ul>
     *   <li>{@code clear @p} so that the item counting in
     *       {@link #assertOneTradeExecutes} starts from a known zero rather
     *       than from whatever a fresh world happens to hand out.</li>
     *   <li>The summon carries <strong>no NBT</strong>. {@code /summon} skips
     *       {@code finalizeSpawn} when an NBT tag is supplied, and while a
     *       wandering trader fills its offers lazily on the first
     *       {@code getOffers} call rather than at finalize time, that is an
     *       implementation detail to lean on for no gain. The NBT is applied
     *       immediately afterwards with {@code data merge}, which reaches the
     *       same state by a route that does not depend on it.</li>
     *   <li>The trader floats one block up ({@code ^ ^1 ^2}), like the pig in
     *       the sibling ToroHealth test and for the same two reasons: the
     *       crosshair ray leaves the eyes at 1.62 above the player's feet, and
     *       a ray that never descends to ground level cannot be occluded by
     *       terrain. Two blocks out keeps the hit well inside the 3.0
     *       {@code entityInteractionRange} that gates the use key.</li>
     * </ul>
     */
    // 26.2 renamed every game rule to snake_case and moved the registry from
    // net.minecraft.world.level.GameRules to net.minecraft.world.level.
    // gamerules.GameRules. Not a cosmetic rename: three of these six also
    // changed identity. doDaylightCycle -> advance_time, doWeatherCycle ->
    // advance_weather, doMobSpawning -> spawn_mobs, and doFireTick (boolean)
    // became fire_spread_radius_around_player (integer, default 128, minimum
    // -1), so "off" is now 0 rather than false. All read off the real 26.2
    // jar's GameRules.<clinit> - each id string is followed there by the
    // putstatic naming its field, and registerInteger's signature is
    // (id, category, default, min).
    //
    // This block was silently wrong on 26.2 for one run before anyone noticed:
    // runCommand swallows command failures, so all six came back "Incorrect
    // argument for command" on the server console while the test went green.
    // None of them are load-bearing - determinism here comes from the NoAI
    // NBT and the crosshair guard below, not from the game rules - which is
    // exactly why the failure was invisible. Read the console, not the tick.
    private static final String[] WORLD_SETUP = {
        //? if <26.2 {
        "gamerule sendCommandFeedback false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "gamerule randomTickSpeed 0",
        "gamerule doFireTick false",
        //?} else {
        /*"gamerule send_command_feedback false",
        "gamerule advance_time false",
        "gamerule advance_weather false",
        "gamerule spawn_mobs false",
        "gamerule random_tick_speed 0",
        "gamerule fire_spread_radius_around_player 0",
        *///?}
        "weather clear",
        "time set noon",
        "difficulty peaceful",
        "gamemode survival @p",
        "clear @p",
        "kill @e[type=!minecraft:player]",
        // Level the pitch first: ^ ^1 ^2 below is a local offset along the
        // player's facing, so summoning while they look at the sky would put
        // the trader out of reach.
        "execute as @p at @s run tp @s ~ ~ ~ ~ 0",
        "execute as @p at @s run summon minecraft:wandering_trader ^ ^1 ^2",
        "data merge entity @e[type=minecraft:wandering_trader,limit=1] "
                + "{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}",
        "execute as @p at @s run tp @s ~ ~ ~ ~ 0",
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.takeScreenshot("evt-title-screen");

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            throw new AssertionError("the client booted without the '" + MOD_ID
                    + "' mod loaded - everything below this point would have passed vacuously");
        }

        assertMerchantScreenMixinApplied();
        assertConfigDefaultMakesTradeDeterministic();

        try (TestSingleplayerContext singleplayer =
                     context.worldBuilder().setUseConsistentSettings(true).create()) {
            for (String command : WORLD_SETUP) {
                singleplayer.getServer().runCommand(command);
            }

            context.waitTicks(SETTLE_TICKS);

            // Before the guard, so that when the guard fires there is a
            // picture of the world it fired in.
            context.takeScreenshot("evt-staged-world");
            assertTraderIsInTheCrosshair(context);

            // The real use key, not a synthetic interact packet: this is the
            // input path a player uses, and it is what makes the screen that
            // opens next evidence about the mod rather than about the test.
            context.getInput().pressKey(options -> options.keyUse);

            assertMerchantMixinSubstitutedTheScreen(context);
            context.takeScreenshot("evt-merchant-screen");

            assertOneTradeExecutes(context, singleplayer);

            // Keep the singleplayer context referenced until here so it is
            // unambiguous that the assertions above ran with the world open.
            singleplayer.getWorldSave();
        }

        // The client gametest runner requires the test to end on the title
        // screen with no server running and no connection open.
        context.waitForScreen(TitleScreen.class);
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    /**
     * Confirms {@code MerchantScreenMixin} was woven into
     * {@code MerchantScreen}.
     *
     * <p>Reflection is a weak form of evidence and is used here only because
     * it is the sole form available: as this class's javadoc explains, nothing
     * in a client gametest can reach {@code postButtonClick}, so this mixin's
     * effect cannot be observed behaviourally the way {@code GuiMerchantMixin}'s
     * can. What it does prove is the failure mode that actually happens -- an
     * injector that resolved against nothing, or a mixin config that was never
     * loaded, leaves the target class untouched and this check fails.
     *
     * <p>Matched with {@code contains} rather than {@code equals}: Mixin is
     * free to rename a merged handler method (commonly by prefixing it with the
     * mixin's own name or a hash), and the exact scheme depends on the Mixin
     * version and whether a refmap was applied. The substring is stable across
     * all of them.
     *
     * <p>Naming {@code MerchantScreen.class} here also forces the class load
     * that triggers transformation, so this runs against the transformed class
     * rather than racing it.
     */
    private static void assertMerchantScreenMixinApplied() {
        for (Method method : MerchantScreen.class.getDeclaredMethods()) {
            if (method.getName().contains("tradeOnSetRecipeIndex")) {
                return;
            }
        }

        StringBuilder found = new StringBuilder();
        for (Method method : MerchantScreen.class.getDeclaredMethods()) {
            if (found.length() > 0) {
                found.append(", ");
            }
            found.append(method.getName());
        }

        throw new AssertionError("MerchantScreenMixin did not apply: no method whose name contains "
                + "'tradeOnSetRecipeIndex' exists on the loaded MerchantScreen class. Since "
                + "mixins.easiervillagertrading.json declares \"required\": true with "
                + "defaultRequire 1, reaching this line at all means the mixin config was never "
                + "loaded for this run (a dev-run mod registration problem, not a mod bug) - a "
                + "genuinely failed injector would have thrown at class load instead. Declared "
                + "methods on MerchantScreen were: [" + found + "]");
    }

    /**
     * Pins the assumption the trade assertion rests on.
     *
     * <p>{@code BetterGuiMerchant.trade} loops until
     * {@code isShiftKeyDown() == shiftSwapped}. No key is held during this
     * test, so with {@code swapShiftBehavior} at its default of {@code false}
     * the condition is true on the first pass and exactly one transaction runs
     * -- which is what lets {@link #assertOneTradeExecutes} assert an exact
     * item delta instead of a range. If that default ever changes, this fails
     * here with the reason rather than fifty lines later as a confusing
     * off-by-a-lot.
     */
    private static void assertConfigDefaultMakesTradeDeterministic() {
        if (EasierVillagerTradingConfig.getInstance().isShiftSwapped()) {
            throw new AssertionError("swapShiftBehavior is enabled, so BetterGuiMerchant.trade "
                    + "will repeat until the villager locks the trade or the inventory runs out. "
                    + "This test's exact one-transaction item accounting assumes the default "
                    + "(false). Either the default changed or a stray config file was picked up "
                    + "from the run directory.");
        }
    }

    /**
     * Confirms the staging worked before the use key is pressed.
     *
     * <p>{@code Minecraft.crosshairPickEntity} is what vanilla itself consults
     * when deciding whether a use-key press becomes an entity interaction, so a
     * wandering trader here proves the summon ran, the entity replicated to the
     * client, the player is facing it, and it is inside interaction range --
     * all four of the ways this staging can fail silently.
     * {@code runCommand} does not throw when a command fails.
     */
    private static void assertTraderIsInTheCrosshair(ClientGameTestContext context) {
        String target = context.computeOnClient(client -> {
            var entity = client.crosshairPickEntity;
            return entity == null ? null : entity.getType().toString();
        });

        if (target == null) {
            throw new AssertionError("nothing is in the crosshair after the world setup, so the "
                    + "use key below would swing at empty air and no merchant screen would ever "
                    + "open - which would fail the flagship assertion for a reason that has "
                    + "nothing to do with this mod. Client-side view of the staging: "
                    + describeStaging(context));
        }

        if (!target.contains("wandering_trader")) {
            throw new AssertionError("expected the summoned wandering trader in the crosshair but "
                    + "found " + target + ". Client-side view of the staging: "
                    + describeStaging(context));
        }
    }

    /**
     * The flagship assertion: pressing use on a merchant opens
     * <em>this mod's</em> screen, not vanilla's.
     *
     * <p>Wrapped so the failure message can say what did open instead. The bare
     * timeout from {@code waitForScreen} reports only that the expected class
     * never appeared, and the three interesting outcomes -- still no screen at
     * all (the interaction never happened), a plain {@code MerchantScreen}
     * (the interaction happened and {@code GuiMerchantMixin} did not fire), or
     * some other screen entirely -- are the whole diagnosis.
     */
    private static void assertMerchantMixinSubstitutedTheScreen(ClientGameTestContext context) {
        try {
            context.waitForScreen(BetterGuiMerchant.class);
        } catch (Throwable timeout) {
            String actual = context.computeOnClient(client -> {
                Screen screen = currentScreen(client);
                return screen == null ? "no screen (the world view)" : screen.getClass().getName();
            });

            boolean guiMixinPresent = false;
            for (Method method : MenuScreens.class.getDeclaredMethods()) {
                if (method.getName().contains("displayVillagerTradeGui")) {
                    guiMixinPresent = true;
                    break;
                }
            }

            throw new AssertionError("pressing the use key on a wandering trader did not open "
                    + "BetterGuiMerchant. Screen actually open: " + actual + ". "
                    + "GuiMerchantMixin's handler "
                    + (guiMixinPresent ? "IS" : "is NOT")
                    + " present on the loaded MenuScreens class, so this is "
                    + (guiMixinPresent
                            ? "a live mixin whose MenuType.MERCHANT branch did not take, or an "
                                    + "interaction that never reached the server at all"
                            : "a mixin that never applied")
                    + ". Client-side view of the staging: " + describeStaging(context),
                    timeout);
        }
    }

    /**
     * Runs one real trade against the live menu and reads the inventory back.
     *
     * <p>The offer is chosen at runtime rather than hardcoded, because a
     * wandering trader's offers are rolled from the trade tables and are not
     * fixed by the world seed. {@link #chooseOffer} states the three properties
     * the accounting below needs; nothing else about the offer matters.
     *
     * <p>The player is given <strong>twice</strong> the cost, not exactly the
     * cost. That is not padding: at exactly the cost, {@code fillSlot} finds
     * {@code remaining} lands on zero and returns -1, and the put-back branch
     * -- the part of the algorithm that moves the unspent remainder of a stack
     * back out of the trade slot, and the part most likely to desync with the
     * server -- never runs at all. At twice the cost it does, and the expected
     * end state is still exact: one cost left, one result gained.
     */
    private static void assertOneTradeExecutes(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        Offer offer = chooseOffer(context);
        if (offer == null) {
            throw new AssertionError("this wandering trader has no offer that the trade accounting "
                    + "below can measure (needs: in stock, a single cost item, and a result item "
                    + "distinct from that cost item). Offers were: " + describeOffers(context));
        }

        int stock = 2 * offer.costCount;
        singleplayer.getServer().runCommand("give @p " + offer.costId + " " + stock);
        context.waitTicks(SETTLE_TICKS);

        int costBefore = countInPlayerInventory(context, offer.costId);
        int resultBefore = countInPlayerInventory(context, offer.resultId);

        if (costBefore != stock) {
            throw new AssertionError("staging the trade failed: gave the player " + stock + " "
                    + offer.costId + " but the open merchant menu shows " + costBefore
                    + " in the inventory slots. Either the give command failed (runCommand does "
                    + "not throw) or the container did not resync, and the trade below would be "
                    + "measured against the wrong baseline.");
        }

        context.takeScreenshot("evt-merchant-stocked");

        context.runOnClient(client -> ((AutoTrade) currentScreen(client)).trade(offer.index));
        context.waitTicks(TRADE_SETTLE_TICKS);

        int costAfter = countInPlayerInventory(context, offer.costId);
        int resultAfter = countInPlayerInventory(context, offer.resultId);
        context.takeScreenshot("evt-after-trade");

        String ledger = String.format(Locale.ROOT,
                "offer %d: %d x %s -> %d x %s; inventory before: %d cost / %d result; "
                        + "after: %d cost / %d result",
                offer.index, offer.costCount, offer.costId, offer.resultCount, offer.resultId,
                costBefore, resultBefore, costAfter, resultAfter);

        if (resultAfter - resultBefore != offer.resultCount) {
            throw new AssertionError("the trade did not deliver its result. " + ledger
                    + ". BetterGuiMerchant.trade ran to completion without throwing, so this is "
                    + "the slot-click protocol failing to complete a transaction against a real "
                    + "server - the first thing to check on a new Minecraft version is the "
                    + "ClickType/ContainerInput split in transact() and slotClick().");
        }

        if (costBefore - costAfter != offer.costCount) {
            throw new AssertionError("the trade delivered its result but did not spend the right "
                    + "cost. " + ledger + ". A shortfall means the put-back branch of fillSlot "
                    + "left items in the trade slots or dropped them; an overspend means the "
                    + "loop ran more than once, which with swapShiftBehavior=false and no key "
                    + "held it must not.");
        }

        System.out.println("[evt-gametest] one trade executed end to end - " + ledger);
    }

    // ------------------------------------------------------------------
    // Client-thread helpers
    // ------------------------------------------------------------------

    /**
     * The screen currently open.
     *
     * <p>The one version branch in this file. {@code Minecraft.screen} is a
     * public field through 1.21.4 and is gone in 26.2, where the field moved
     * onto {@code Gui} behind the accessor {@code screen()} -- established by
     * disassembling {@code Minecraft.setScreenAndShow}, which delegates
     * straight to {@code this.gui.setScreen(screen)}. This is the same break
     * {@code GuiMerchantMixin} already carries a branch for.
     */
    private static Screen currentScreen(Minecraft client) {
        //? if <26.2 {
        return client.screen;
        //?} else {
        /*return client.gui.screen();
        *///?}
    }

    /**
     * The merchant menu behind the currently open screen.
     *
     * <p>Deliberately routed through the screen rather than through
     * {@code client.player.containerMenu}: the point is to read the same menu
     * object {@code BetterGuiMerchant.trade} will operate on.
     */
    private static MerchantMenu merchantMenu(Minecraft client) {
        Screen screen = currentScreen(client);
        if (!(screen instanceof BetterGuiMerchant)) {
            throw new AssertionError("expected BetterGuiMerchant to still be open but found "
                    + (screen == null ? "no screen" : screen.getClass().getName())
                    + " - the merchant screen closed part way through the trade assertion");
        }
        return ((BetterGuiMerchant) screen).getMenu();
    }

    /**
     * Picks an offer whose outcome can be counted unambiguously.
     *
     * <p>Three requirements, each load-bearing for the accounting in
     * {@link #assertOneTradeExecutes}: in stock (or {@code trade}'s loop
     * refuses to run at all), a single cost item (a second cost would have to
     * be stocked and counted too, for no extra coverage), and a result item
     * that is not the same item as the cost (otherwise the two deltas land in
     * the same inventory stack and neither can be read).
     */
    private static Offer chooseOffer(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            MerchantOffers offers = merchantMenu(client).getOffers();
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer offer = offers.get(i);
                if (offer.isOutOfStock()) {
                    continue;
                }
                ItemStack costA = offer.getCostA();
                ItemStack result = offer.getResult();
                if (costA.isEmpty() || !offer.getCostB().isEmpty()) {
                    continue;
                }
                if (result.isEmpty() || result.getItem() == costA.getItem()) {
                    continue;
                }
                return new Offer(i, itemId(costA), costA.getCount(),
                        itemId(result), result.getCount());
            }
            return null;
        });
    }

    /** Everything the trade assertion needs to know, read off the client once. */
    private static final class Offer {
        private final int index;
        private final String costId;
        private final int costCount;
        private final String resultId;
        private final int resultCount;

        private Offer(int index, String costId, int costCount, String resultId, int resultCount) {
            this.index = index;
            this.costId = costId;
            this.costCount = costCount;
            this.resultId = resultId;
            this.resultCount = resultCount;
        }
    }

    /**
     * Counts one item across the player-inventory portion of the open menu.
     *
     * <p>The last 36 slots, which is the same window
     * {@code BetterGuiMerchant}'s own {@code hasEnoughItemsInInventory},
     * {@code canReceiveOutput}, {@code fillSlot} and {@code getslot} all scan.
     * Reading through the menu rather than through {@code Player.getInventory}
     * is the point: it is the container view the algorithm acts on, so a
     * desync between the two would show up here rather than being papered over.
     */
    private static int countInPlayerInventory(ClientGameTestContext context, String itemId) {
        return context.computeOnClient(client -> {
            MerchantMenu menu = merchantMenu(client);
            int total = 0;
            for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!stack.isEmpty() && itemId(stack).equals(itemId)) {
                    total += stack.getCount();
                }
            }
            return total;
        });
    }

    /**
     * The registry id of a stack's item, as a string.
     *
     * <p>A string rather than the registry key object because
     * {@code ResourceLocation} was renamed {@code Identifier} in 26.x and this
     * file has one version branch already; the string form is also what the
     * {@code give} command needs.
     */
    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /** Every offer the open trader has, for the failure message in {@link #assertOneTradeExecutes}. */
    private static String describeOffers(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            MerchantOffers offers = merchantMenu(client).getOffers();
            if (offers.isEmpty()) {
                return "none at all - the trader's offer list is empty, which normally means the "
                        + "entity was spawned in a way that skipped trade generation";
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer offer = offers.get(i);
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(String.format(Locale.ROOT, "[%d] %d x %s + %d x %s -> %d x %s%s",
                        i,
                        offer.getCostA().getCount(), itemId(offer.getCostA()),
                        offer.getCostB().getCount(), itemId(offer.getCostB()),
                        offer.getResult().getCount(), itemId(offer.getResult()),
                        offer.isOutOfStock() ? " (out of stock)" : ""));
            }
            return text.toString();
        });
    }

    /**
     * What the client can actually see, for the staging failure messages.
     *
     * <p>Separates "the trader was never summoned" from "it exists but the
     * player is not looking at it" from "it exists, is in front of the player,
     * and is out of interaction range". Asking the client rather than the
     * server also proves the entity replicated -- the crosshair pick runs
     * client-side, so a trader that exists only on the server is invisible to
     * it.
     */
    private static String describeStaging(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            // `var` throughout rather than the concrete types: LocalPlayer and
            // Entity both moved package in 26.x, and this file has to compile
            // unchanged on 1.21.4 and 26.2 alike.
            var player = client.player;
            if (player == null) {
                return "there is no client player at all";
            }

            StringBuilder nearby = new StringBuilder();
            if (client.level != null) {
                for (var entity : client.level.getEntities(player,
                        player.getBoundingBox().inflate(16.0))) {
                    if (nearby.length() > 0) {
                        nearby.append(", ");
                    }
                    nearby.append(String.format(Locale.ROOT, "%s at (%.2f, %.2f, %.2f) %.2fm away",
                            entity.getType(), entity.getX(), entity.getY(), entity.getZ(),
                            entity.distanceTo(player)));
                }
            }

            return String.format(Locale.ROOT,
                    "player at (%.2f, %.2f, %.2f) eyes y=%.2f looking yaw=%.1f pitch=%.1f; "
                            + "entities within 16m: [%s]",
                    player.getX(), player.getY(), player.getZ(), player.getEyeY(),
                    player.getYRot(), player.getXRot(),
                    nearby.length() == 0 ? "none" : nearby.toString());
        });
    }
}
