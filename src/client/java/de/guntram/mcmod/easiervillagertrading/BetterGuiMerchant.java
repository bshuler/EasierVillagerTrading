package de.guntram.mcmod.easiervillagertrading;

// Every loader this repo targets (Fabric, Forge, NeoForge) compiles this
// mod's client sourceSet against the SAME mapping set under this
// Stonecraft/Architectury-Loom setup: a layered mapping using Mojang's
// official class/method/field names as the primary layer (confirmed by
// extracting the actual clientCompileClasspath/compileClasspath jars for
// every cell - fabric, legacy Forge, and NeoForge alike all ship
// net/minecraft/client/gui/screens/inventory/MerchantScreen.class,
// net/minecraft/world/inventory/MerchantMenu.class, etc; NOT the Yarn names
// net/minecraft/client/gui/screen/ingame/MerchantScreen or
// net/minecraft/screen/MerchantScreenHandler that an earlier pass of this
// file assumed for Fabric/legacy-Forge). That earlier "Fabric+Forge share
// Yarn, only NeoForge uses Mojang" theory was wrong - re-verified this pass
// via direct javap against the real per-version clientonly/common jars for
// 1.18.2, 1.19.4, 1.20.1 and 1.21.4, all four came back identical Mojang
// package/class/field/method names for every symbol this file touches. So
// there is exactly one implementation here, shared by every loader.
//
// The one genuine API difference across the version range is the
// stack-merge-equivalence check: ItemStack.isSameItemSameTags(a, b) exists
// through 1.20.1 (NBT-tag based); the 1.20.5 item-component rewrite removed
// it in favor of ItemStack.isSameItemSameComponents(a, b). Confirmed via
// javap: 1.18.2/1.19.4 have only isSameItemSameTags; 1.20.1 has both
// isSameItemSameTags and isSameItem (transitional); 1.21.4 has
// isSameItemSameComponents and isSameItem but no isSameItemSameTags. Since
// this repo's oldest 1.20-series target is 1.20.1 (pre-rewrite), the split
// is a clean "<1.20.5 tags / >=1.20.5 components" version conditional below.
//
// A second, later API break: MC 26.2 removed net.minecraft.world.inventory.
// ClickType entirely (confirmed via javap against the real cached 26.2
// minecraft-common-deobf jar - the class file is gone). Its slotClicked-4th-
// parameter role is now played by net.minecraft.world.inventory.ContainerInput,
// a same-named-constants enum (PICKUP/QUICK_MOVE/SWAP/CLONE/THROW/PICKUP_ALL,
// plus a new QUICK_CRAFT) - a straight rename, not a semantic redesign. Also
// in 26.2: Screen.hasShiftDown()/hasControlDown() (static utility methods
// through 1.21.4) are gone from Screen - javap on the real 26.2 Screen.class
// shows no such methods - moved onto a per-input-event instance method
// (net.minecraft.client.input.InputWithModifiers, implemented by KeyEvent/
// MouseButtonEvent). This mixin's injection point has no such event object
// available, so isShiftKeyDown() below reads the raw key state directly via
// com.mojang.blaze3d.platform.InputConstants.isKeyDown(Window, keycode)
// instead - the same primitive Screen.hasShiftDown() itself used to wrap.
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
//? if <26.2 {
import net.minecraft.world.inventory.ClickType;
//?} else {
/*import net.minecraft.world.inventory.ContainerInput;
*///?}
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Subclass of the vanilla merchant screen that repeats a trade on
 * click/shift-click. This is upstream's core algorithm (gbl/EasierVillagerTrading),
 * unchanged in substance across every version/loader this fork targets -
 * only the item-merge check (see comment above) and the config lookup below
 * (swapped to the vendored config, see CLAUDE.md) differ from upstream.
 *
 * @author gbl (original), ported by this fork
 */
public class BetterGuiMerchant extends MerchantScreen implements AutoTrade {

    public BetterGuiMerchant(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    public void trade(int tradeIndex) {

        boolean shiftSwapped = EasierVillagerTradingConfig.getInstance().isShiftSwapped();

        MerchantOffers trades = menu.getOffers();
        MerchantOffer recipe = trades.get(tradeIndex);
        int safeguard = 0;
        while (!recipe.isOutOfStock()
                && inputSlotsAreEmpty()
                && hasEnoughItemsInInventory(recipe)
                && canReceiveOutput(recipe.getResult())) {
            transact(recipe);
            if (isShiftKeyDown() == shiftSwapped || ++safeguard > 50) {
                break;
            }
        }
    }

    //? if <26.2 {
    private boolean isShiftKeyDown() {
        return hasShiftDown();
    }
    //?} else {
    /*private boolean isShiftKeyDown() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
    }
    *///?}

    private boolean inputSlotsAreEmpty() {
        return menu.getSlot(0).getItem().isEmpty()
                && menu.getSlot(1).getItem().isEmpty()
                && menu.getSlot(2).getItem().isEmpty();
    }

    private boolean hasEnoughItemsInInventory(MerchantOffer recipe) {
        if (!hasEnoughItemsInInventory(recipe.getCostA())) {
            return false;
        }
        return hasEnoughItemsInInventory(recipe.getCostB());
    }

    private boolean hasEnoughItemsInInventory(ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack invstack = menu.getSlot(i).getItem();
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
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack invstack = menu.getSlot(i).getItem();
            if (invstack == null || invstack.isEmpty()) {
                return true;
            }
            if (areItemStacksMergable(stack, invstack)
                    && stack.getMaxStackSize() >= stack.getCount() + invstack.getCount()) {
                remaining -= (invstack.getMaxStackSize() - invstack.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void transact(MerchantOffer recipe) {
        int putback0, putback1;
        putback0 = fillSlot(0, recipe.getCostA());
        putback1 = fillSlot(1, recipe.getCostB());

        getslot(2, recipe.getResult(), putback0, putback1);
        if (putback0 != -1) {
            slotClick(0);
            slotClick(putback0);
        }
        if (putback1 != -1) {
            slotClick(1);
            slotClick(putback1);
        }
        // This is a serious hack.
        // AbstractContainerMenu checks:
        //    if (actionType == ClickType.SWAP && clickData >= 0 && clickData < 9)
        // so this is a NOP on (a normal) server, but our mixin can watch for it and force an inventory resend.
        //? if <26.2 {
        this.slotClicked(null, /* slot*/ 0, /* clickData*/ 99, ClickType.SWAP);
        //?} else {
        /*this.slotClicked(null, 0, 99, ContainerInput.SWAP);
        *///?}
    }

    /**
     * @param slot  the number of the (trading) slot that should receive items
     * @param stack what the trading slot should receive
     * @return the number of the inventory slot into which these items should be put back
     * after the transaction. May be -1 if nothing needs to be put back.
     */
    private int fillSlot(int slot, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack invstack = menu.getSlot(i).getItem();
            if (invstack == null) {
                continue;
            }
            boolean needPutBack = false;
            if (areItemStacksMergable(stack, invstack)) {
                if (stack.getCount() + invstack.getCount() > stack.getMaxStackSize()) {
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
                && (!a.isDamageableItem() || a.getDamageValue() == b.getDamageValue())
                //? if <1.20.5 {
                /*&& ItemStack.isSameItemSameTags(a, b);
                *///?} else {
                && ItemStack.isSameItemSameComponents(a, b);
                //?}
    }

    private void getslot(int slot, ItemStack stack, int... forbidden) {
        int remaining = stack.getCount();
        slotClick(slot);
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack invstack = menu.getSlot(i).getItem();
            if (invstack == null || invstack.isEmpty()) {
                continue;
            }
            if (areItemStacksMergable(stack, invstack)
                    && invstack.getCount() < invstack.getMaxStackSize()) {
                remaining -= (invstack.getMaxStackSize() - invstack.getCount());
                slotClick(i);
            }
            if (remaining <= 0) {
                return;
            }
        }

        // When looking for an empty slot, don't take one that we want to put some input back to.
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            boolean isForbidden = false;
            for (int f : forbidden) {
                if (i == f) {
                    isForbidden = true;
                }
            }
            if (isForbidden) {
                continue;
            }
            ItemStack invstack = menu.getSlot(i).getItem();
            if (invstack == null || invstack.isEmpty()) {
                slotClick(i);
                return;
            }
        }
    }

    private void slotClick(int slot) {
        //? if <26.2 {
        this.slotClicked(null, slot, 0, ClickType.PICKUP);
        //?} else {
        /*this.slotClicked(null, slot, 0, ContainerInput.PICKUP);
        *///?}
    }
}
