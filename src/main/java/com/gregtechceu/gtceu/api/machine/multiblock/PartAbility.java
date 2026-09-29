package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.function.Supplier;

public class PartAbility {

    private static final List<PartAbility> ALL = new ArrayList<>();

    public static final PartAbility EXPORT_ITEMS = new PartAbility("export_items", "gtceu.part_ability.export_items");
    public static final PartAbility IMPORT_ITEMS = new PartAbility("import_items", "gtceu.part_ability.import_items");
    public static final PartAbility EXPORT_FLUIDS = new PartAbility("export_fluids", "gtceu.part_ability.export_fluids");
    public static final PartAbility IMPORT_FLUIDS = new PartAbility("import_fluids", "gtceu.part_ability.import_fluids");
    public static final PartAbility DUAL_INPUT = new PartAbility("dual_input", "gtceu.part_ability.dual_input");
    public static final PartAbility DUAL_OUTPUT = new PartAbility("dual_output", "gtceu.part_ability.dual_output");
    public static final PartAbility EXPORT_FLUIDS_1X = new PartAbility("export_fluids_1x", "gtceu.part_ability.export_fluids_1x");
    public static final PartAbility IMPORT_FLUIDS_1X = new PartAbility("import_fluids_1x", "gtceu.part_ability.import_fluids_1x");
    public static final PartAbility EXPORT_FLUIDS_4X = new PartAbility("export_fluids_4x", "gtceu.part_ability.export_fluids_4x");
    public static final PartAbility IMPORT_FLUIDS_4X = new PartAbility("import_fluids_4x", "gtceu.part_ability.import_fluids_4x");
    public static final PartAbility EXPORT_FLUIDS_9X = new PartAbility("export_fluids_9x", "gtceu.part_ability.export_fluids_9x");
    public static final PartAbility IMPORT_FLUIDS_9X = new PartAbility("import_fluids_9x", "gtceu.part_ability.import_fluids_9x");
    public static final PartAbility INPUT_ENERGY = new PartAbility("input_energy", "gtceu.part_ability.input_energy");
    public static final PartAbility OUTPUT_ENERGY = new PartAbility("output_energy", "gtceu.part_ability.output_energy");
    public static final PartAbility SUBSTATION_INPUT_ENERGY = new PartAbility("substation_input_energy", "gtceu.part_ability.substation_input_energy");
    public static final PartAbility SUBSTATION_OUTPUT_ENERGY = new PartAbility("substation_output_energy", "gtceu.part_ability.substation_output_energy");
    public static final PartAbility ROTOR_HOLDER = new PartAbility("rotor_holder", "gtceu.part_ability.rotor_holder");
    public static final PartAbility PUMP_FLUID_HATCH = new PartAbility("pump_fluid_hatch", "gtceu.part_ability.pump_fluid_hatch");
    public static final PartAbility STEAM = new PartAbility("steam", "gtceu.part_ability.steam");
    public static final PartAbility STEAM_IMPORT_ITEMS = new PartAbility("steam_import_items", "gtceu.part_ability.steam_import_items");
    public static final PartAbility STEAM_EXPORT_ITEMS = new PartAbility("steam_export_items", "gtceu.part_ability.steam_export_items");
    public static final PartAbility MAINTENANCE = new PartAbility("maintenance", "gtceu.part_ability.maintenance");
    public static final PartAbility MUFFLER = new PartAbility("muffler", "gtceu.part_ability.muffler");
    public static final PartAbility TANK_VALVE = new PartAbility("tank_valve", "gtceu.part_ability.tank_valve");
    public static final PartAbility PASSTHROUGH_HATCH = new PartAbility("passthrough_hatch", "gtceu.part_ability.passthrough_hatch");
    public static final PartAbility PARALLEL_HATCH = new PartAbility("parallel_hatch", "gtceu.part_ability.parallel_hatch");
    public static final PartAbility INPUT_LASER = new PartAbility("input_laser", "gtceu.part_ability.input_laser");
    public static final PartAbility OUTPUT_LASER = new PartAbility("output_laser", "gtceu.part_ability.output_laser");
    public static final PartAbility COMPUTATION_DATA_RECEPTION = new PartAbility("computation_data_reception", "gtceu.part_ability.computation_data_reception");
    public static final PartAbility COMPUTATION_DATA_TRANSMISSION = new PartAbility("computation_data_transmission", "gtceu.part_ability.computation_data_transmission");
    public static final PartAbility OPTICAL_DATA_RECEPTION = new PartAbility("optical_data_reception", "gtceu.part_ability.optical_data_reception");
    public static final PartAbility OPTICAL_DATA_TRANSMISSION = new PartAbility("optical_data_transmission", "gtceu.part_ability.optical_data_transmission");
    public static final PartAbility DATA_ACCESS = new PartAbility("data_access", "gtceu.part_ability.data_access");
    public static final PartAbility HPCA_COMPONENT = new PartAbility("hpca_component", "gtceu.part_ability.hpca_component");
    public static final PartAbility UTILITY = new PartAbility("utility", "gtceu.part_ability.utility");
    public static final PartAbility MACHINE_CONTROL = new PartAbility("machine_control", "gtceu.part_ability.machine_control");
    /**
     * tier -> available blocks
     */
    private final Int2ObjectOpenHashMap<Set<Block>> registry = new Int2ObjectOpenHashMap<>();
    private final Supplier<Collection<Block>> allBlocks = GTMemoizer.memoize(() -> {
        List<Block> result = new ArrayList<>();
        var entries = new ArrayList<>(registry.int2ObjectEntrySet());
        entries.sort(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey));
        for (var entry : entries) {
            result.addAll(entry.getValue());
        }
        result.sort(Comparator.comparingInt(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().length()));
        return result;
    });
    @Getter
    private final String name;
    @Getter
    private final String translationKey;

    public PartAbility(String name, String translationKey) {
        this.name = name;
        this.translationKey = translationKey;
        synchronized (ALL) {
            ALL.add(this);
        }
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }

    public static Component join(PartAbility... abilities) {
        var result = Component.empty();
        for (int i = 0; i < abilities.length; i++) {
            if (i > 0) result.append(" / ");
            result.append(abilities[i].getDisplayName());
        }
        return result;
    }

    public static List<PartAbility> getAll() {
        synchronized (ALL) {
            return List.copyOf(ALL);
        }
    }

    public void register(int tier, Block block) {
        registry.computeIfAbsent(tier, T -> new ReferenceOpenHashSet<>()).add(block);
    }

    public Collection<Block> getAllBlocks() {
        return allBlocks.get();
    }

    public boolean isApplicable(Block block) {
        return getAllBlocks().contains(block);
    }

    public Collection<Block> getBlocks(int... tiers) {
        List<Block> result = new ArrayList<>();
        for (ObjectIterator<Int2ObjectMap.Entry<Set<Block>>> it = registry.int2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var e = it.next();
            if (ArrayUtils.contains(tiers, e.getIntKey())) {
                result.addAll(e.getValue());
            }
        }
        return result;
    }

    /**
     * [from, to]
     */
    public Collection<Block> getBlockRange(int from, int to) {
        List<Block> result = new ArrayList<>();
        for (ObjectIterator<Int2ObjectMap.Entry<Set<Block>>> it = registry.int2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var e = it.next();
            var key = e.getIntKey();
            if (key >= from && key <= to) {
                result.addAll(e.getValue());
            }
        }
        return result;
    }
}
