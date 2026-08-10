package de.guntram.mcmod.easiervillagertrading;

// This whole file is Fabric-only for now: it mixes into vanilla merchant-
// screen classes using Yarn mapping names (MerchantScreen, MerchantScreenHandler).
// Forge/NeoForge use Mojang's official mappings, which name the same
// classes differently - porting this requires re-deriving every target name
// against Mojmap for each MC version. See CLAUDE.md and PLAN.md. Until that
// happens, this class simply doesn't exist on Forge/NeoForge builds.
//? if fabric {
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

/**
 * Subclass of the vanilla merchant screen that repeats a trade on
 * click/shift-click. This is upstream's core algorithm (gbl/EasierVillagerTrading),
 * unchanged in substance across every Fabric branch from 1.18.2 through
 * 1.20.4 - only the config lookup below has been swapped to the vendored
 * config (see CLAUDE.md).
 *
 * @author gbl (original), ported by this fork
 */
public class BetterGuiMerchant extends MerchantScreen implements AutoTrade {

    public BetterGuiMerchant(MerchantScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
    }

    @Override
    public void trade(int tradeIndex) {

        boolean shiftSwapped = EasierVillagerTradingConfig.getInstance().isShiftSwapped();

        TradeOfferList trades = handler.getRecipes();
        TradeOffer recipe = trades.get(tradeIndex);
        int safeguard = 0;
        while (!recipe.isDisabled()
                && inputSlotsAreEmpty()
                && hasEnoughItemsInInventory(recipe)
                && canReceiveOutput(recipe.getSellItem())) {
            transact(recipe);
            if (hasShiftDown() == shiftSwapped || ++safeguard > 50) {
                break;
            }
        }
    }

    private boolean inputSlotsAreEmpty() {
        return handler.getSlot(0).getStack().isEmpty()
                && handler.getSlot(1).getStack().isEmpty()
                && handler.getSlot(2).getStack().isEmpty();
    }

    private boolean hasEnoughItemsInInventory(TradeOffer recipe) {
        if (!hasEnoughItemsInInventory(recipe.getAdjustedFirstBuyItem())) {
            return false;
        }
        return hasEnoughItemsInInventory(recipe.getSecondBuyItem());
    }

    private boolean hasEnoughItemsInInventory(ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack invstack = handler.getSlot(i).getStack();
            if (invstack == null) {
                continue;
            }
            if (areItemStacksMergable(stack, invstack)) {
                remaining -= invstack.getCount();
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canReceiveOutput(ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack invstack = handler.getSlot(i).getStack();
            if (invstack == null || invstack.isEmpty()) {
                return true;
            }
            if (areItemStacksMergable(stack, invstack)
                    && stack.getMaxCount() >= stack.getCount() + invstack.getCount()) {
                remaining -= (invstack.getMaxCount() - invstack.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void transact(TradeOffer recipe) {
        int putback0, putback1;
        putback0 = fillSlot(0, recipe.getAdjustedFirstBuyItem());
        putback1 = fillSlot(1, recipe.getSecondBuyItem());

        getslot(2, recipe.getSellItem(), putback0, putback1);
        if (putback0 != -1) {
            slotClick(0);
            slotClick(putback0);
        }
        if (putback1 != -1) {
            slotClick(1);
            slotClick(putback1);
        }
        // This is a serious hack.
        // ScreenHandler checks:
        //    if (actionType == SlotActionType.SWAP && clickData >= 0 && clickData < 9)
        // so this is a NOP on (a normal) server, but our mixin can watch for it and force an inventory resend.
        this.onMouseClick(null, /* slot*/ 0, /* clickData*/ 99, SlotActionType.SWAP);
    }

    /**
     * @param slot  the number of the (trading) slot that should receive items
     * @param stack what the trading slot should receive
     * @return the number of the inventory slot into which these items should be put back
     * after the transaction. May be -1 if nothing needs to be put back.
     */
    private int fillSlot(int slot, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack invstack = handler.getSlot(i).getStack();
            if (invstack == null) {
                continue;
            }
            boolean needPutBack = false;
            if (areItemStacksMergable(stack, invstack)) {
                if (stack.getCount() + invstack.getCount() > stack.getMaxCount()) {
                    needPutBack = true;
                }
                remaining -= invstack.getCount();
                slotClick(i);
                slotClick(slot);
            }
            if (needPutBack) {
                slotClick(i);
            }
            if (remaining <= 0) {
                return remaining < 0 ? i : -1;
            }
        }
        // We should not be able to arrive here, since hasEnoughItemsInInventory should have been
        // called before fillSlot. But if we do, something went wrong; in this case better do a bit less.
        return -1;
    }

    private boolean areItemStacksMergable(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getItem() == b.getItem()
                && (!a.isDamageable() || a.getDamage() == b.getDamage())
                && ItemStack.canCombine(a, b);
    }

    private void getslot(int slot, ItemStack stack, int... forbidden) {
        int remaining = stack.getCount();
        slotClick(slot);
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack invstack = handler.getSlot(i).getStack();
            if (invstack == null || invstack.isEmpty()) {
                continue;
            }
            if (areItemStacksMergable(stack, invstack)
                    && invstack.getCount() < invstack.getMaxCount()) {
                remaining -= (invstack.getMaxCount() - invstack.getCount());
                slotClick(i);
            }
            if (remaining <= 0) {
                return;
            }
        }

        // When looking for an empty slot, don't take one that we want to put some input back to.
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            boolean isForbidden = false;
            for (int f : forbidden) {
                if (i == f) {
                    isForbidden = true;
                }
            }
            if (isForbidden) {
                continue;
            }
            ItemStack invstack = handler.getSlot(i).getStack();
            if (invstack == null || invstack.isEmpty()) {
                slotClick(i);
                return;
            }
        }
    }

    private void slotClick(int slot) {
        this.onMouseClick(null, slot, 0, SlotActionType.PICKUP);
    }
}
//?}
