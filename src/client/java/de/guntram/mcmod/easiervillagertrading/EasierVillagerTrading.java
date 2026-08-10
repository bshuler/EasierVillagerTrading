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
 * Mod entry point. On Fabric this loads the (vendored) config and relies on
 * fabric.mod.json to wire up the mixins that do the actual work. NeoForge
 * and Forge builds currently only load config on client setup - the
 * merchant-screen mixins have not been ported to Mojang-mapped targets yet,
 * see PLAN.md.
 */
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
    /*@Mod(MODID)
    public EasierVillagerTrading(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        loadConfig(FMLPaths.CONFIGDIR.get().toFile());
    }
    *///?} elif forge {
    /*@Mod(MODID)
    public EasierVillagerTrading() {
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
