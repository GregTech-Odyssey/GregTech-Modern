package com.gregtechceu.gtceu.api.addon;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

public interface IGTAddon {

    /**
     * @return this addon's GTRegistrate instance.
     */
    GTRegistrate getRegistrate();

    /**
     * this addon's Mod id.
     * 
     * @return the Mod ID this addon uses for content.
     */
    String addonModId();

    /**
     * Call init on your custom TagPrefix class(es) here
     */
    default void registerTagPrefixes() {}

    /**
     * Call init on your custom Element class(es) here
     */
    default void registerElements() {}

    /**
     * Call init on your custom Material class(es) here
     */
    default void registerMaterials() {}

    /**
     * Call init on your custom Sound class(es) here
     */
    default void registerSounds() {}

    /**
     * Call init on your custom Cover class(es) here
     */
    default void registerCovers() {}

    default void registerMachiness() {}

    default void registerDimensionMarkers() {}

    /**
     * Call init on your custom Recipe Capabilities here
     */
    default void registerRecipeCapabilities() {}

    default void registerRecipeTypes() {}

    default void registerRecipeCategories() {}

    default void registerRecipeDataKey() {}

    /**
     * Call init on your custom IWorldGenLayer class(es) here
     */
    default void registerWorldgenLayers() {}

    /**
     * Call init on your custom VeinGenerator class(es) here
     */
    default void registerVeinGenerators() {}

    /**
     * Call init on your custom IndicatorGenerator class(es) here
     */
    default void registerIndicatorGenerators() {}

    /**
     * Does this addon require high-tier content to be enabled?
     * 
     * @return if this addon requires highTier.
     */
    default boolean requiresHighTier() {
        return false;
    }
}
