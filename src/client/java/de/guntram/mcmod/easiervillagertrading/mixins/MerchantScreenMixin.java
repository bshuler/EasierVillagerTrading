package de.guntram.mcmod.easiervillagertrading.mixins;

// Fabric-only: targets Yarn-mapped vanilla classes. See BetterGuiMerchant.java
// for why this doesn't (yet) have a Forge/NeoForge equivalent.
//? if fabric {
import de.guntram.mcmod.easiervillagertrading.AutoTrade;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link AutoTrade#trade} whenever the player selects a trade in the
 * merchant screen (i.e. whenever the game syncs the selected recipe index
 * back to the server). Unchanged from upstream (gbl/EasierVillagerTrading).
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler> {

    @Shadow
    private int selectedIndex;

    public MerchantScreenMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "syncRecipeIndex", at = @At("RETURN"))
    public void tradeOnSetRecipeIndex(CallbackInfo ci) {
        if (Screen.hasControlDown()) {
            return;
        }
        this.onMouseClick(null, 0, 0, SlotActionType.QUICK_MOVE);
        this.onMouseClick(null, 1, 0, SlotActionType.QUICK_MOVE);

        ((AutoTrade) this).trade(selectedIndex);
    }
}
//?}
