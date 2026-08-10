package de.guntram.mcmod.easiervillagertrading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the vendored config persistence class - the one class in this mod with no
 * Minecraft import at all, and therefore the only genuinely headless-testable class. See
 * CLAUDE.md / PLAN.md "Testing" for why everything else (mixins, the merchant-screen
 * subclass, the loader entry point) is excluded from coverage instead.
 *
 * Each test instantiates its own {@code EasierVillagerTradingConfig} via the public no-arg
 * constructor rather than going through the process-wide {@link EasierVillagerTradingConfig#getInstance()}
 * singleton, so tests don't leak state into each other.
 */
class EasierVillagerTradingConfigTest {

    private static final String FILE_NAME = "easiervillagertrading.properties";
    private static final String KEY_SWAP_SHIFT = "swapShiftBehavior";

    @Test
    void getInstance_returnsTheSameSingletonAcrossCalls() {
        EasierVillagerTradingConfig first = EasierVillagerTradingConfig.getInstance();
        EasierVillagerTradingConfig second = EasierVillagerTradingConfig.getInstance();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    void isShiftSwapped_defaultsToFalseOnAFreshInstance() {
        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();

        assertFalse(config.isShiftSwapped());
    }

    @Test
    void save_beforeLoadHasEverBeenCalled_isANoOpAndDoesNotThrow(@TempDir File tempDir) {
        // configFile is only ever set by load(); calling save() first must not NPE.
        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();

        assertDoesNotThrow(config::save);

        // Nothing should have been written anywhere under the temp dir either.
        assertEquals(0, tempDir.listFiles().length);
    }

    @Test
    void load_whenConfigFileIsMissing_createsItWithDefaultFalseAndPersists(@TempDir File tempDir)
            throws IOException {
        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();

        config.load(tempDir);

        assertFalse(config.isShiftSwapped());
        File written = new File(tempDir, FILE_NAME);
        assertTrue(written.exists(), "load() should create the properties file when absent");

        Properties onDisk = new Properties();
        try (var in = Files.newInputStream(written.toPath())) {
            onDisk.load(in);
        }
        assertEquals("false", onDisk.getProperty(KEY_SWAP_SHIFT));
    }

    @Test
    void load_whenConfigFileAlreadyHasSwapTrue_readsTrue(@TempDir File tempDir) throws IOException {
        File existing = new File(tempDir, FILE_NAME);
        Properties props = new Properties();
        props.setProperty(KEY_SWAP_SHIFT, "true");
        try (var out = Files.newOutputStream(existing.toPath())) {
            props.store(out, "pre-seeded for test");
        }

        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();
        config.load(tempDir);

        assertTrue(config.isShiftSwapped());
    }

    @Test
    void load_whenConfigFileHasNoSwapKey_defaultsToFalse(@TempDir File tempDir) throws IOException {
        File existing = new File(tempDir, FILE_NAME);
        Properties props = new Properties();
        try (var out = Files.newOutputStream(existing.toPath())) {
            props.store(out, "no swap key present");
        }

        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();
        config.load(tempDir);

        assertFalse(config.isShiftSwapped());
    }

    @Test
    void load_whenConfigPathIsADirectory_fallsBackToDefaultAndSubsequentSaveDegradesGracefully(
            @TempDir File tempDir) {
        // Force the properties "file" to actually be a directory so both load()'s
        // FileInputStream and save()'s FileOutputStream fail with an IOException at open
        // time, exercising the catch branch of both try-with-resources blocks without
        // depending on filesystem-permission behaviour (which varies by OS/user).
        File asDirectory = new File(tempDir, FILE_NAME);
        assertTrue(asDirectory.mkdir());

        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();

        assertDoesNotThrow(() -> config.load(tempDir));
        assertFalse(config.isShiftSwapped(), "unreadable config must fall back to the default");

        // In-memory state still updates even though persistence is impossible; save() must
        // swallow the IOException rather than propagate it.
        assertDoesNotThrow(() -> config.setShiftSwapped(true));
        assertTrue(config.isShiftSwapped());
    }

    @Test
    void setShiftSwapped_updatesInMemoryValueAndPersistsToDisk(@TempDir File tempDir) throws IOException {
        EasierVillagerTradingConfig config = new EasierVillagerTradingConfig();
        config.load(tempDir);

        config.setShiftSwapped(true);
        assertTrue(config.isShiftSwapped());

        File written = new File(tempDir, FILE_NAME);
        Properties onDisk = new Properties();
        try (var in = Files.newInputStream(written.toPath())) {
            onDisk.load(in);
        }
        assertEquals("true", onDisk.getProperty(KEY_SWAP_SHIFT));

        // A fresh instance loading the same directory should observe the persisted value.
        EasierVillagerTradingConfig reloaded = new EasierVillagerTradingConfig();
        reloaded.load(tempDir);
        assertTrue(reloaded.isShiftSwapped());
    }
}
