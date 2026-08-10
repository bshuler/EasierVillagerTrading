package de.guntram.mcmod.easiervillagertrading;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Minimal vendored replacement for upstream's GBfabrictools-based
 * configuration handler. Persists exactly one setting - whether Shift-click
 * and plain-click behaviour should be swapped - to a plain properties file
 * in the given config directory.
 *
 * See CLAUDE.md for why this doesn't depend on GBfabrictools/ModMenu.
 */
public class EasierVillagerTradingConfig {

    private static final String FILE_NAME = "easiervillagertrading.properties";
    private static final String KEY_SWAP_SHIFT = "swapShiftBehavior";

    private static EasierVillagerTradingConfig instance;

    private boolean swapShiftBehavior = false;
    private File configFile;

    public static EasierVillagerTradingConfig getInstance() {
        if (instance == null) {
            instance = new EasierVillagerTradingConfig();
        }
        return instance;
    }

    public void load(File configDir) {
        configFile = new File(configDir, FILE_NAME);
        if (!configFile.exists()) {
            save();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configFile)) {
            props.load(in);
            swapShiftBehavior = Boolean.parseBoolean(
                    props.getProperty(KEY_SWAP_SHIFT, "false"));
        } catch (IOException e) {
            // Fall back to defaults if the file is unreadable; don't crash the game over a config file.
        }
    }

    public void save() {
        if (configFile == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY_SWAP_SHIFT, Boolean.toString(swapShiftBehavior));
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "EasierVillagerTrading configuration");
        } catch (IOException e) {
            // Nothing useful to do if we can't write the config; the in-memory value still applies this session.
        }
    }

    public boolean isShiftSwapped() {
        return swapShiftBehavior;
    }

    public void setShiftSwapped(boolean value) {
        swapShiftBehavior = value;
        save();
    }
}
