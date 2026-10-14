package com.gregtechceu.gtceu.data.lang;

import com.gto.registrate.providers.RegistrateLangProvider;

public class ToolLang {

    public static void init(RegistrateLangProvider provider) {
        initDeathMessages(provider);
        initToolInfo(provider);
    }

    private static void initDeathMessages(RegistrateLangProvider provider) {
        provider.add("death.attack.gtceu.heat", "%s was boiled alive");
        provider.add("death.attack.gtceu.frost", "%s explored cryogenics");
        provider.add("death.attack.gtceu.chemical", "%s had a chemical accident");
        provider.add("death.attack.gtceu.electric", "%s was electrocuted");
        provider.add("death.attack.gtceu.radiation", "%s glows with joy now");
        provider.add("death.attack.gtceu.turbine", "%s put their head into a turbine");
        provider.add("death.attack.gtceu.explosion", "%s exploded");
        provider.add("death.attack.gtceu.explosion.player", "%s exploded with help of %s");
        provider.add("death.attack.gtceu.heat.player", "%s was boiled alive by %s");

        provider.add("death.attack.gtceu.medical_condition/asbestosis", "%s got mesothelioma");
        provider.add("death.attack.gtceu.medical_condition/chemical_burns", "%s had a chemical accident");
        provider.add("death.attack.gtceu.medical_condition/poison",
                "%s forgot that poisonous materials are, in fact, poisonous");
        provider.add("death.attack.gtceu.medical_condition/silicosis",
                "%s didn't die of tuberculosis. it was silicosis.");
        provider.add("death.attack.gtceu.medical_condition/arsenicosis", "%s got arsenic poisoning");
        provider.add("death.attack.gtceu.medical_condition/berylliosis", "%s mined emeralds a bit too greedily");
        provider.add("death.attack.gtceu.medical_condition/carcinogen", "%s got leukemia");
        provider.add("death.attack.gtceu.medical_condition/irritant", "%s got a §n§lREALLY§r bad rash");
        provider.add("death.attack.gtceu.medical_condition/methanol_poisoning",
                "%s tried to drink moonshine during the prohibition");
        provider.add("death.attack.gtceu.medical_condition/nausea", "%s died of nausea");
        provider.add("death.attack.gtceu.medical_condition/none", "%s died of... nothing?");
        provider.add("death.attack.gtceu.medical_condition/weak_poison", "%s ate lead (or mercury!)");
        provider.add("death.attack.gtceu.medical_condition/carbon_monoxide_poisoning", "%s left the stove on");
    }

    private static void initToolInfo(RegistrateLangProvider provider) {}
}
