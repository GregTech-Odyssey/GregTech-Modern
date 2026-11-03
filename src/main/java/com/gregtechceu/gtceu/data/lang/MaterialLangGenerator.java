package com.gregtechceu.gtceu.data.lang;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.gto.registrate.providers.RegistrateLangProvider;

public class MaterialLangGenerator {

    public static void generate(RegistrateLangProvider provider, String namespace) {
        for (Material material : GTRegistries.MATERIALS) {
            if (material.getModid().equals(namespace)) provider.add(material.getUnlocalizedName(), material.getEnglishName());
        }
    }
}
