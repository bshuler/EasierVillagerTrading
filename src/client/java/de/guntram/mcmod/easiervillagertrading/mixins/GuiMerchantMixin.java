package de.guntram.mcmod.easiervillagertrading.mixins;

// Fabric, legacy Forge and NeoForge all compile this client sourceSet
// against the same Mojang-mapped names under this repo's Loom setup (see
// BetterGuiMerchant.java for the jar-inspection evidence trail across all
// four cached MC versions) - one implementation, no per-loader branch.
import de.guntram.mcmod.easiervillagertrading.BetterGuiMerchant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps in {@link BetterGuiMerchant} whenever the server opens a vanilla
 * merchant screen. Unchanged in substance from upstream (gbl/EasierVillagerTrading);
 * only the mapping (Mojang official names, not Yarn) differs from upstream's
 * own Fabric-only source.
 */
@Mixin(MenuScreens.class)
public abstract class GuiMerchantMixin {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void displayVillagerTradeGui(MenuType type, Minecraft client,
            int syncId, Component title, CallbackInfo ci) {

        if (type == MenuType.MERCHANT) {
            MerchantMenu container = MenuType.MERCHANT.create(syncId, client.player.getInventory());
            BetterGuiMerchant screen = new BetterGuiMerchant(container, client.player.getInventory(), title);
            client.player.containerMenu = container;
            // Minecraft.setScreen(Screen) was renamed setScreenAndShow(Screen) in
            // MC 26.2 (confirmed via javap: setScreen is gone from the real
            // cached 26.2 Minecraft.class, only setScreenAndShow remains).
            //? if <26.2 {
            client.setScreen(screen);
            //?} else {
            /*client.setScreenAndShow(screen);
            *///?}
            ci.cancel();
        }
    }
}
