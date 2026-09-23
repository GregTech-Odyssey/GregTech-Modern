package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class MetaMachineConfigCopyBehaviour implements IInteractionItem, IAddInformation {

    public static final String NONE_DIRECTION = "null";

    public static final String CONFIG_DATA = "config_data";
    public static final String ORIGINAL_FRONT = "front";
    public static final String ITEM_CONFIG = "item";
    public static final String FLUID_CONFIG = "fluid";
    public static final String DIRECTION = "direction";
    public static final String AUTO = "auto";
    public static final String INPUT_FROM_OUTPUT_SIDE = "in_from_out";
    public static final String MUFFLED = "muffled";

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

    public static String directionToString(@Nullable Direction direction) {
        if (direction == null) return NONE_DIRECTION;
        return direction.getSerializedName();
    }

    public static @Nullable Direction tagToDirection(@Nullable Tag tag) {
        if (tag instanceof StringTag string) {
            String name = string.getAsString();
            if (Strings.isNullOrEmpty(name) || NONE_DIRECTION.equalsIgnoreCase(name)) return null;
            return Direction.byName(name);
        } else if (tag instanceof NumericTag number) {
            // backwards compatibility
            int ordinal = number.getAsInt();
            return ordinal <= 0 || ordinal > Direction.values().length ? null : Direction.values()[ordinal - 1];
        }
        return null;
    }

    public static Component relativeDirectionComponent(Direction origFront, Direction origDirection) {
        RelativeDirection relative = RelativeDirection.findRelativeOf(origFront, origDirection);
        return DIRECTION_TOOLTIPS[relative.ordinal()];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        var stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            stack.removeTagKey(CONFIG_DATA);
            return InteractionResultHolder.success(stack);
        }
        return IInteractionItem.super.use(item, level, player, usedHand);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // spotless:off
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MetaMachineBlockEntity blockEntity) {
            var machine = blockEntity.getMetaMachine();
            if (!MachineOwner.canOpenOwnerMachine(context.getPlayer(), machine)) {
                return InteractionResult.FAIL;
            }
            if (context.isSecondaryUseActive()) {
                return handleCopy(stack, machine);
            } else if (stack.getTagElement(CONFIG_DATA) != null) {
                return handlePaste(stack, machine);
            }
        } else if (context.isSecondaryUseActive() && context.getLevel().getBlockState(context.getClickedPos()).isAir()) {
            stack.removeTagKey(CONFIG_DATA);
            return InteractionResult.SUCCESS;
        }
        // spotless:on
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleCopy(ItemStack stack, MetaMachine machine) {
        CompoundTag configData = new CompoundTag();
        configData.putString(ORIGINAL_FRONT, directionToString(machine.getFrontFacing()));
        if (machine instanceof IAutoOutputItem autoOutputItem && autoOutputItem.getOutputFacingItems() != null) {
            configData.put(ITEM_CONFIG, copyOutputConfig(autoOutputItem.getOutputFacingItems(),
                    autoOutputItem.isAutoOutputItems(), autoOutputItem.isAllowInputFromOutputSideItems()));
        }
        if (machine instanceof IAutoOutputFluid autoOutputFluid && autoOutputFluid.getOutputFacingFluids() != null) {
            configData.put(FLUID_CONFIG, copyOutputConfig(autoOutputFluid.getOutputFacingFluids(),
                    autoOutputFluid.isAutoOutputFluids(), autoOutputFluid.isAllowInputFromOutputSideFluids()));
        }
        if (machine instanceof IMufflableMachine mufflableMachine) {
            configData.putBoolean(MUFFLED, mufflableMachine.isMuffled());
        }
        if (!configData.isEmpty()) {
            stack.getOrCreateTag().put(CONFIG_DATA, configData);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handlePaste(ItemStack stack, MetaMachine machine) {
        CompoundTag configData = stack.getTagElement(CONFIG_DATA);
        if (configData == null) return InteractionResult.PASS;
        Direction originalFront = tagToDirection(configData.get(ORIGINAL_FRONT));
        if (configData.contains(ITEM_CONFIG) && machine instanceof IAutoOutputItem autoOutputItem) {
            pasteOutputConfig(originalFront, machine.getFrontFacing(), configData.getCompound(ITEM_CONFIG),
                    autoOutputItem::setOutputFacingItems, autoOutputItem::setAutoOutputItems,
                    autoOutputItem::setAllowInputFromOutputSideItems);
        }
        if (configData.contains(FLUID_CONFIG) && machine instanceof IAutoOutputFluid autoOutputFluid) {
            pasteOutputConfig(originalFront, machine.getFrontFacing(), configData.getCompound(FLUID_CONFIG),
                    autoOutputFluid::setOutputFacingFluids, autoOutputFluid::setAutoOutputFluids,
                    autoOutputFluid::setAllowInputFromOutputSideFluids);
        }
        if (configData.contains(MUFFLED) && machine instanceof IMufflableMachine mufflableMachine) {
            mufflableMachine.setMuffled(configData.getBoolean(MUFFLED));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * GTO: 按电力盔甲 tooltip 的格式列出可复制的每一项，并在同一行显示卡内存储的值。
     */
    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        CompoundTag data = stack.getTagElement(CONFIG_DATA);
        MutableComponent header = Component.literal("◆ ")
                .append(Component.translatable("behaviour.memory_card.gto.section.copyable"));
        if (data == null) {
            header.append(Component.literal(" · ").append(Component.translatable("behaviour.memory_card.gto.empty"))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(header.withStyle(ChatFormatting.GOLD));

        Direction origFront = data == null ? null : tagToDirection(data.get(ORIGINAL_FRONT));
        addSetting(tooltipComponents, Component.translatable("behaviour.memory_card.gto.setting.output_side"), data,
                config -> {
                    Direction side = tagToDirection(config.get(DIRECTION));
                    if (origFront == null || side == null) return notRecorded();
                    return relativeDirectionComponent(origFront, side).copy().withStyle(ChatFormatting.WHITE);
                });
        addSetting(tooltipComponents, Component.translatable("behaviour.memory_card.gto.setting.auto_output"), data,
                config -> onOff(config.getBoolean(AUTO)));
        addSetting(tooltipComponents, Component.translatable("behaviour.memory_card.gto.setting.input_from_output"),
                data, config -> onOff(config.getBoolean(INPUT_FROM_OUTPUT_SIDE)));
        tooltipComponents.add(settingLine(Component.translatable("behaviour.memory_card.gto.setting.muffled"),
                data == null ? null : data.contains(MUFFLED) ? onOff(data.getBoolean(MUFFLED)) : notRecorded()));

        tooltipComponents.add(Component.literal("◆ ")
                .append(Component.translatable("behaviour.memory_card.gto.section.usage"))
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal(" ").append(Component.translatable("behaviour.memory_card.gto.usage.copy"))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(" ")
                .append(Component.translatable("behaviour.memory_card.gto.usage.paste"))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal(" ")
                .append(Component.translatable("behaviour.memory_card.gto.usage.clear"))
                .withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal(" ")
                    .append(Component.translatable("behaviour.memory_card.gto.details.relative"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.literal(" ")
                    .append(Component.translatable("behaviour.memory_card.gto.details.skip"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.literal(" ")
                    .append(Component.translatable("behaviour.memory_card.gto.details.not_copied"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.translatable("metaarmor.gto.hint.shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component notRecorded() {
        return Component.translatable("behaviour.memory_card.gto.not_recorded").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component onOff(boolean on) {
        return on ? Component.translatable("behaviour.memory_card.gto.on").withStyle(ChatFormatting.GREEN) :
                Component.translatable("behaviour.memory_card.gto.off").withStyle(ChatFormatting.RED);
    }

    /**
     * 功能行「▸ 名称 值」；value 为 null（卡内为空）时只显示名称
     */
    private static Component settingLine(MutableComponent name, @Nullable Component value) {
        MutableComponent line = Component.literal(" ▸ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(name.withStyle(ChatFormatting.WHITE));
        if (value != null) line.append("  ").append(value);
        return line;
    }

    /**
     * 物品、流体各记录一份的设置，同一行依次列出「物品 值 · 流体 值」
     */
    private static void addSetting(List<Component> tooltip, MutableComponent name, @Nullable CompoundTag data,
                                   Function<CompoundTag, Component> valueOf) {
        MutableComponent value = null;
        if (data != null) {
            for (RecipeInfo cap : GTRegistries.RECIPE_INFOS) {
                if (!data.contains(cap.name)) continue;
                if (value == null) value = Component.empty();
                else value.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
                value.append(cap.getColoredName()).append(" ").append(valueOf.apply(data.getCompound(cap.name)));
            }
            if (value == null) value = notRecorded().copy();
        }
        tooltip.add(settingLine(name, value));
    }

    private static CompoundTag copyOutputConfig(Direction outputSide, boolean autoOutput,
                                                boolean allowInputFromOutputSide) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIRECTION, directionToString(outputSide));
        tag.putBoolean(AUTO, autoOutput);
        tag.putBoolean(INPUT_FROM_OUTPUT_SIDE, allowInputFromOutputSide);
        return tag;
    }

    private static void pasteOutputConfig(Direction originalFront, Direction currentFront, CompoundTag data,
                                          Consumer<Direction> outputSide, BooleanConsumer autoOutput,
                                          BooleanConsumer allowInputFromOutputSide) {
        outputSide.accept(rotateSide(originalFront, currentFront, tagToDirection(data.get(DIRECTION))));
        autoOutput.accept(data.getBoolean(AUTO));
        allowInputFromOutputSide.accept(data.getBoolean(INPUT_FROM_OUTPUT_SIDE));
    }

    /**
     * Keeps {@code side} at the same position relative to the machine front, using the same convention as the
     * tooltip ({@link RelativeDirection#findRelativeOf}), so e.g. a bottom output stays at the bottom.
     */
    private static @Nullable Direction rotateSide(@Nullable Direction originalFront, Direction currentFront,
                                                  @Nullable Direction side) {
        if (originalFront == null || side == null) return side;
        RelativeDirection relative = RelativeDirection.findRelativeOf(originalFront, side);
        for (Direction dir : Direction.values()) {
            if (RelativeDirection.findRelativeOf(currentFront, dir) == relative) return dir;
        }
        return side;
    }
}
