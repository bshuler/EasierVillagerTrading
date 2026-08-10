package de.guntram.mcmod.easiervillagertrading.mixins;

// Fabric, legacy Forge and NeoForge all compile this client sourceSet
// against the same Mojang-mapped names under this repo's Loom setup (see
// BetterGuiMerchant.java for the jar-inspection evidence trail) - one
// implementation, no per-loader branch.
//
// Mixin target derivation: Mojang's MerchantScreen has no method literally
// named syncRecipeIndex (that's Yarn's name). javap -c against the real
// merged-mojang jar (verified identical across 1.18.2/1.19.4/1.20.1/1.21.4)
// showed the equivalent flow is MerchantScreen.postButtonClick() (private,
// called from the TradeOfferButton's onPress lambda right after `shopItem`
// is set) - it runs menu.setSelectionHint(shopItem)/menu.tryMoveItems(shopItem)
// then sends the trade-selection packet, i.e. exactly the "recipe index was
// just synced" moment Yarn's syncRecipeIndex(RETURN) hooks. `shopItem` is the
// Mojang-mapped field playing the same role as Yarn's `selectedIndex`.
// MC 26.2 removed net.minecraft.world.inventory.ClickType (replaced by the
// same-named-constants net.minecraft.world.inventory.ContainerInput enum -
// see BetterGuiMerchant.java for the javap evidence trail) and removed the
// static Screen.hasControlDown()/hasShiftDown() utility methods (moved onto
// per-input-event instances of InputWithModifiers, e.g. KeyEvent/
// MouseButtonEvent - not available at this mixin's injection point, so
// isControlKeyDown() below reads the raw key state directly instead).
import de.guntram.mcmod.easiervillagertrading.AutoTrade;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
//? if <26.2 {
import net.minecraft.world.inventory.ClickType;
//?} else {
/*import net.minecraft.world.inventory.ContainerInput;
*///?}
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link AutoTrade#trade} whenever the player selects a trade in the
 * merchant screen (i.e. whenever the game syncs the selected recipe index
 * back to the server). Unchanged in substance from upstream
 * (gbl/EasierVillagerTrading); only the mixin target name and mapping
 * (Mojang official, not Yarn) differ from upstream's own Fabric-only source.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Shadow
    private int shopItem;

    public MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "postButtonClick", at = @At("RETURN"))
    public void tradeOnSetRecipeIndex(CallbackInfo ci) {
        if (isControlKeyDown()) {
            return;
        }
        //? if <26.2 {
        this.slotClicked(null, 0, 0, ClickType.QUICK_MOVE);
        this.slotClicked(null, 1, 0, ClickType.QUICK_MOVE);
        //?} else {
        /*this.slotClicked(null, 0, 0, ContainerInput.QUICK_MOVE);
        this.slotClicked(null, 1, 0, ContainerInput.QUICK_MOVE);
        *///?}

        ((AutoTrade) this).trade(shopItem);
    }

    //? if <26.2 {
    private boolean isControlKeyDown() {
        return Screen.hasControlDown();
    }
    //?} else {
    /*private boolean isControlKeyDown() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }
    *///?}
}
