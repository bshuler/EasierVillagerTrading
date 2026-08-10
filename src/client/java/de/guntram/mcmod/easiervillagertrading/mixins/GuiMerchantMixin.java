package de.guntram.mcmod.easiervillagertrading.mixins;

// Fabric-only: targets Yarn-mapped vanilla classes. See BetterGuiMerchant.java
// for why this doesn't (yet) have a Forge/NeoForge equivalent.
//? if fabric {
import de.guntram.mcmod.easiervillagertrading.BetterGuiMerchant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps in {@link BetterGuiMerchant} whenever the server opens a vanilla
 * merchant screen. Unchanged from upstream (gbl/EasierVillagerTrading).
 */
@Mixin(HandledScreens.class)
public abstract class GuiMerchantMixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static void displayVillagerTradeGui(ScreenHandlerType type, MinecraftClient client,
            int syncId, Text title, CallbackInfo ci) {

        if (type == ScreenHandlerType.MERCHANT) {
            MerchantScreenHandler container = ScreenHandlerType.MERCHANT.create(syncId, client.player.getInventory());
            BetterGuiMerchant screen = new BetterGuiMerchant(container, client.player.getInventory(), title);
            client.player.currentScreenHandler = container;
            client.setScreen(screen);
            ci.cancel();
        }
    }
}
//?}
