package de.guntram.mcmod.easiervillagertrading;

import java.io.File;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
*///?} elif forge {
/*import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
*///?}

/**
 * Mod entry point. Loads the (vendored) config on client setup; the actual
 * merchant-screen mixins (BetterGuiMerchant, GuiMerchantMixin,
 * MerchantScreenMixin - see mixins.easiervillagertrading.json) are wired up
 * per-loader via fabric.mod.json ("client" entrypoint + mixins array) on
 * Fabric, and via mods.toml/neoforge.mods.toml's [[mixins]] block on
 * Forge/NeoForge - see PLAN.md.
 */
// Forge/NeoForge reflectively instantiate whichever class in the mod jar
// carries @Mod(modid) (scanning for the annotation on the TYPE, not on a
// constructor - putting it on the constructor instead compiles as "annotation
// type not applicable to this kind of declaration", caught by an actual
// compileClientJava run against real Forge-mapped classes). Fabric has no
// class-level annotation equivalent; entrypoints are declared in
// fabric.mod.json instead.
//? if forge || neoforge {
/*@Mod(EasierVillagerTrading.MODID)
*///?}
public class EasierVillagerTrading
//? if fabric {
implements ClientModInitializer
//?}
{

    public static final String MODID = "easiervillagertrading";
    public static final String MODNAME = "EasierVillagerTrading";

    //? if fabric {
    @Override
    public void onInitializeClient() {
        loadConfig(FabricLoader.getInstance().getConfigDir().toFile());
    }
    //?} elif neoforge {
    /*public EasierVillagerTrading(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        loadConfig(FMLPaths.CONFIGDIR.get().toFile());
    }
    *///?} elif forge {
    /*public EasierVillagerTrading() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        loadConfig(FMLPaths.CONFIGDIR.get().toFile());
    }
    *///?}

    private static void loadConfig(File configDir) {
        EasierVillagerTradingConfig.getInstance().load(configDir);
    }
}
