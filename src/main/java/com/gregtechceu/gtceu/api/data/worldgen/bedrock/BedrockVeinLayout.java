package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

interface BedrockVeinLayout<D extends BedrockVeinDefinition> {

    /**
     * Fill the entry of the given chunk; it stays missing when the chunk has no vein. The result must depend only on
     * the
     * chunk, never on which chunks were generated before.
     */
    void generate(BedrockVeinSavedData<D> data, int chunkX, int chunkZ);
}
