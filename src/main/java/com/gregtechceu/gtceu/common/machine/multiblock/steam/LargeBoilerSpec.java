package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 大型锅炉的注册数据（{@code registerLargeBoiler} 登记，按注册顺序编号，青铜为 0），锅炉等级与参数一律从这里取。
 */
public record LargeBoilerSpec(int tier, String name, MultiblockMachineDefinition definition, BoilerFireboxType firebox,
                              int maxTemperature, int heatSpeed) {

    private static final List<LargeBoilerSpec> SPECS = new ArrayList<>();
    private static final List<LargeBoilerSpec> VIEW = Collections.unmodifiableList(SPECS);
    private static final Map<MachineDefinition, LargeBoilerSpec> BY_DEFINITION = new Reference2ObjectOpenHashMap<>();

    public static LargeBoilerSpec register(String name, MultiblockMachineDefinition definition, BoilerFireboxType firebox,
                                           int maxTemperature, int heatSpeed) {
        var spec = new LargeBoilerSpec(SPECS.size(), name, definition, firebox, maxTemperature, heatSpeed);
        SPECS.add(spec);
        BY_DEFINITION.put(definition, spec);
        return spec;
    }

    public static List<LargeBoilerSpec> all() {
        return VIEW;
    }

    @Nullable
    public static LargeBoilerSpec of(MachineDefinition definition) {
        return BY_DEFINITION.get(definition);
    }
}
