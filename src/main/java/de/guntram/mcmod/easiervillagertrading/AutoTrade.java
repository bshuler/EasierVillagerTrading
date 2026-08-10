package de.guntram.mcmod.easiervillagertrading;

/**
 * Implemented by the merchant screen subclass that knows how to repeat a
 * trade. Kept as its own interface (unchanged from upstream) so the mixin
 * that triggers it doesn't need to depend on the concrete screen class.
 */
public interface AutoTrade {

    void trade(int tradeIndex);
}
