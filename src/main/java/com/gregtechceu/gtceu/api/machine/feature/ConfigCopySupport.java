package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.feature.IVoidable.VoidingMode;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IInputLimitableMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Toolbox behind {@link IConfigCopyable}: the NBT keys, the read/write building blocks machines
 * compose their config from, and the tooltip registry the memory card previews stored config with.
 * <p>
 * The card itself only calls {@link #appendStoredTooltips} — it needs that because at item-tooltip
 * time there is no machine instance to ask, only the stored {@link CompoundTag}. Everything else in
 * here is called <b>by machines</b>, from their {@code writeConfigTo} / {@code readConfigFrom}, so
 * each machine explicitly declares which settings it contributes instead of being passively scanned.
 * <p>
 * NBT keys are unchanged from before the refactor, so memory cards already written in a world stay
 * valid. Addons adding their own copyable settings should namespace their keys (e.g.
 * {@code "yourmodid:setting"}) and register a matching tooltip renderer.
 */
public final class ConfigCopySupport {

    private ConfigCopySupport() {}

    // --- NBT keys (unchanged, for backward compatibility with existing cards) ---
    /** Front facing of the machine the config was copied from; written by the card itself. */
    public static final String ORIGINAL_FRONT = "front";
    public static final String NONE_DIRECTION = "null";
    public static final String ITEM_CONFIG = "item";
    public static final String FLUID_CONFIG = "fluid";
    public static final String DIRECTION = "direction";
    public static final String AUTO = "auto";
    public static final String INPUT_FROM_OUTPUT_SIDE = "in_from_out";
    public static final String MUFFLED = "muffled";
    public static final String VOIDING_MODE = "voiding_mode";
    public static final String INPUT_LIMIT = "input_limit";
    public static final String DISTINCT = "distinct";
    public static final String CIRCUIT = "circuit";

    public static final Component ENABLED = Component.translatable("cover.voiding.label.enabled")
            .withStyle(ChatFormatting.GREEN);
    public static final Component DISABLED = Component.translatable("cover.voiding.label.disabled")
            .withStyle(ChatFormatting.RED);

    public static final Component[] DIRECTION_TOOLTIPS = {
            Component.translatable("gtceu.direction.tooltip.up").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gtceu.direction.tooltip.down").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gtceu.direction.tooltip.left").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gtceu.direction.tooltip.right").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gtceu.direction.tooltip.front").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gtceu.direction.tooltip.back").withStyle(ChatFormatting.YELLOW),
    };

    // ------------------------------------------------------------------
    // Direction helpers (shared by the card and by the machine-side blocks)
    // ------------------------------------------------------------------

    public static String directionToString(@Nullable Direction direction) {
        return direction == null ? NONE_DIRECTION : direction.getSerializedName();
    }

    public static @Nullable Direction tagToDirection(@Nullable Tag tag) {
        if (tag instanceof StringTag string) {
            String name = string.getAsString();
            if (name.isEmpty() || NONE_DIRECTION.equalsIgnoreCase(name)) return null;
            return Direction.byName(name);
        } else if (tag instanceof NumericTag number) {
            // backwards compatibility
            int ordinal = number.getAsInt();
            return ordinal <= 0 || ordinal > Direction.values().length ? null : Direction.values()[ordinal - 1];
        }
        return null;
    }

    /** Front facing of the source machine, needed to re-map output sides relative to the target. */
    public static @Nullable Direction readOriginalFront(CompoundTag tag) {
        return tagToDirection(tag.get(ORIGINAL_FRONT));
    }

    public static Component relativeDirectionComponent(Direction origFront, Direction origDirection) {
        return DIRECTION_TOOLTIPS[RelativeDirection.findRelativeOf(origFront, origDirection).ordinal()];
    }

    // ------------------------------------------------------------------
    // Tooltip registry (machine-independent, so the card can preview
    // stored config with only the ItemStack's NBT in hand).
    // ------------------------------------------------------------------

    /** Renders stored config into the Shift-hover tooltip using only the NBT. */
    @FunctionalInterface
    public interface TooltipRenderer {

        void render(CompoundTag tag, List<Component> tooltip);
    }

    // Ordered so preview lines render deterministically. Registration happens in this class's
    // static initializer (and, for addons, during mod setup) — always before any tooltip render.
    private static final ObjectArrayList<TooltipRenderer> TOOLTIP_RENDERERS = new ObjectArrayList<>();

    /**
     * Register a renderer that inspects the stored tag itself and decides what (if anything) to add.
     * <p>
     * Must be called during mod setup; iteration in {@link #appendStoredTooltips} is unsynchronized,
     * so registering after tooltip rendering has started is undefined behavior.
     */
    public static synchronized void registerTooltip(TooltipRenderer renderer) {
        TOOLTIP_RENDERERS.add(renderer);
    }

    /** Convenience: only render when {@code key} is present in the stored config. */
    public static void registerTooltip(String key, TooltipRenderer renderer) {
        registerTooltip((tag, tooltip) -> {
            if (tag.contains(key)) renderer.render(tag, tooltip);
        });
    }

    /** Card-side: render every stored config entry, in registration order. */
    public static void appendStoredTooltips(CompoundTag tag, List<Component> tooltip) {
        // Indexed access, not an iterator — but go through get(i): elements() hands back the raw
        // Object[] backing array, which cannot be cast to TooltipRenderer[].
        for (int i = 0, size = TOOLTIP_RENDERERS.size(); i < size; i++) {
            TOOLTIP_RENDERERS.get(i).render(tag, tooltip);
        }
    }

    // ------------------------------------------------------------------
    // Auto output (item / fluid)
    // ------------------------------------------------------------------

    public static void writeAutoOutputItem(CompoundTag tag, IAutoOutputItem machine) {
        // Same guard the directional config UI uses — machines without output slots have no setting.
        if (!machine.hasAutoOutputItem()) return;
        Direction side = machine.getOutputFacingItems();
        if (side == null) return;
        tag.put(ITEM_CONFIG, writeOutputConfig(side, machine.isAutoOutputItems(),
                machine.isAllowInputFromOutputSideItems()));
    }

    public static void readAutoOutputItem(CompoundTag tag, IAutoOutputItem machine) {
        if (!machine.hasAutoOutputItem()) return;
        readOutputConfig(tag, ITEM_CONFIG, machine, machine::setOutputFacingItems,
                machine::setAutoOutputItems, machine::setAllowInputFromOutputSideItems);
    }

    public static void writeAutoOutputFluid(CompoundTag tag, IAutoOutputFluid machine) {
        if (!machine.hasAutoOutputFluid()) return;
        Direction side = machine.getOutputFacingFluids();
        if (side == null) return;
        tag.put(FLUID_CONFIG, writeOutputConfig(side, machine.isAutoOutputFluids(),
                machine.isAllowInputFromOutputSideFluids()));
    }

    public static void readAutoOutputFluid(CompoundTag tag, IAutoOutputFluid machine) {
        if (!machine.hasAutoOutputFluid()) return;
        readOutputConfig(tag, FLUID_CONFIG, machine, machine::setOutputFacingFluids,
                machine::setAutoOutputFluids, machine::setAllowInputFromOutputSideFluids);
    }

    private static CompoundTag writeOutputConfig(@Nullable Direction outputSide, boolean autoOutput,
                                                 boolean allowInputFromOutputSide) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIRECTION, directionToString(outputSide));
        tag.putBoolean(AUTO, autoOutput);
        tag.putBoolean(INPUT_FROM_OUTPUT_SIDE, allowInputFromOutputSide);
        return tag;
    }

    private static void readOutputConfig(CompoundTag tag, String key, IMachineFeature machine,
                                         Consumer<Direction> outputSide, BooleanConsumer autoOutput,
                                         BooleanConsumer allowInputFromOutputSide) {
        if (!tag.contains(key)) return;
        // The output side is stored relative to the source machine's front, so without it we cannot
        // re-map onto this machine — skip rather than write a wrong side.
        Direction originalFront = readOriginalFront(tag);
        if (originalFront == null) return;
        CompoundTag data = tag.getCompound(key);
        outputSide.accept(RelativeDirection.getActualDirection(originalFront,
                machine.self().getFrontFacing(), tagToDirection(data.get(DIRECTION))));
        autoOutput.accept(data.getBoolean(AUTO));
        allowInputFromOutputSide.accept(data.getBoolean(INPUT_FROM_OUTPUT_SIDE));
    }

    // ------------------------------------------------------------------
    // Muffling
    // ------------------------------------------------------------------

    public static void writeMuffled(CompoundTag tag, IMufflableMachine machine) {
        tag.putBoolean(MUFFLED, machine.isMuffled());
    }

    public static void readMuffled(CompoundTag tag, IMufflableMachine machine) {
        if (tag.contains(MUFFLED)) {
            machine.setMuffled(tag.getBoolean(MUFFLED));
        }
    }

    // ------------------------------------------------------------------
    // Voiding mode / input limit / circuit
    // ------------------------------------------------------------------

    public static void writeVoiding(CompoundTag tag, IVoidable voidable) {
        if (voidable.hasVoidingModeConfig()) {
            tag.putString(VOIDING_MODE, voidable.getVoidingMode().name());
        }
    }

    public static void readVoiding(CompoundTag tag, IVoidable voidable) {
        if (tag.contains(VOIDING_MODE) && voidable.hasVoidingModeConfig()) {
            VoidingMode mode = parseVoidingMode(tag.getString(VOIDING_MODE));
            if (mode != null) {
                voidable.setVoidingMode(mode);
            }
        }
    }

    public static void writeInputLimit(CompoundTag tag, IInputLimitableMachine machine) {
        if (machine.hasInputLimitConfig()) {
            tag.putBoolean(INPUT_LIMIT, machine.isInputLimit());
        }
    }

    public static void readInputLimit(CompoundTag tag, IInputLimitableMachine machine) {
        if (tag.contains(INPUT_LIMIT) && machine.hasInputLimitConfig()) {
            machine.setInputLimit(tag.getBoolean(INPUT_LIMIT));
        }
    }

    public static void writeDistinct(CompoundTag tag, IDistinctPart part) {
        if (part.hasDistinctConfig()) {
            tag.putBoolean(DISTINCT, part.isDistinct());
        }
    }

    public static void readDistinct(CompoundTag tag, IDistinctPart part) {
        if (tag.contains(DISTINCT) && part.hasDistinctConfig()) {
            part.setDistinct(tag.getBoolean(DISTINCT));
        }
    }

    public static void writeCircuit(CompoundTag tag, ICircuitConfigurable machine) {
        if (machine.hasCircuitConfig()) {
            tag.putInt(CIRCUIT, machine.getCircuitConfiguration());
        }
    }

    public static void readCircuit(CompoundTag tag, ICircuitConfigurable machine) {
        if (tag.contains(CIRCUIT) && machine.hasCircuitConfig()) {
            machine.setCircuitConfiguration(tag.getInt(CIRCUIT));
        }
    }

    /** Lenient enum parse: null/empty/unknown/renamed values are skipped instead of crashing a paste. */
    public static @Nullable VoidingMode parseVoidingMode(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return VoidingMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Built-in previews, in display order. Loaded when the card first
    // touches this class through appendStoredTooltips.
    // ------------------------------------------------------------------
    static {
        // Auto output: one block per recipe capability actually present in the stored config.
        registerTooltip((data, tooltip) -> {
            Direction origFront = readOriginalFront(data);
            if (origFront == null) return;
            for (RecipeInfo cap : GTRegistries.RECIPE_INFOS) {
                if (!data.contains(cap.name)) continue;
                CompoundTag config = data.getCompound(cap.name);
                Component name = cap.getColoredName();
                tooltip.add(Component.translatable("behaviour.setting.output.direction.tooltip", name,
                        relativeDirectionComponent(origFront, tagToDirection(config.get(DIRECTION)))));
                tooltip.add(Component.translatable("behaviour.setting.item_auto_output.tooltip", name,
                        config.getBoolean(AUTO) ? ENABLED : DISABLED));
                tooltip.add(Component.translatable("behaviour.setting.allow.input.from.output.tooltip", name,
                        config.getBoolean(INPUT_FROM_OUTPUT_SIDE) ? ENABLED : DISABLED));
            }
        });

        registerTooltip(MUFFLED, (data, tooltip) -> tooltip.add(Component
                .translatable("behaviour.setting.muffled.tooltip", data.getBoolean(MUFFLED) ? ENABLED : DISABLED)));

        registerTooltip(VOIDING_MODE, (data, tooltip) -> {
            VoidingMode mode = parseVoidingMode(data.getString(VOIDING_MODE));
            Component modeText = mode == null ?
                    Component.literal(data.getString(VOIDING_MODE)).withStyle(ChatFormatting.YELLOW) :
                    Component.translatable(mode.getSerializedName() + ".1").withStyle(ChatFormatting.YELLOW);
            tooltip.add(Component.translatable("behaviour.setting.voiding_mode.tooltip", modeText));
        });

        registerTooltip(INPUT_LIMIT, (data, tooltip) -> tooltip.add(Component
                .translatable("behaviour.setting.input_limit.tooltip",
                        data.getBoolean(INPUT_LIMIT) ? ENABLED : DISABLED)));

        registerTooltip(DISTINCT, (data, tooltip) -> tooltip.add(Component
                .translatable("behaviour.setting.distinct.tooltip",
                        data.getBoolean(DISTINCT) ? ENABLED : DISABLED)));

        registerTooltip(CIRCUIT, (data, tooltip) -> {
            int circuit = data.getInt(CIRCUIT);
            Component circuitText = circuit < 0 ?
                    Component.translatable("behaviour.setting.circuit.none").withStyle(ChatFormatting.GRAY) :
                    Component.literal(Integer.toString(circuit)).withStyle(ChatFormatting.YELLOW);
            tooltip.add(Component.translatable("behaviour.setting.circuit.tooltip", circuitText));
        });
    }
}
