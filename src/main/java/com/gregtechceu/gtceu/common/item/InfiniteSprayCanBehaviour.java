package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IDurabilityBar;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Backs the reusable infinite spray can: a single item that holds the selected color in NBT
 * instead of baking it into the item like {@link ColorSprayBehaviour}.
 * Left click cycles the color, middle click opens the palette, right click paints. The mouse
 * handling lives client-side; this component only reacts to the right click and the GUI.
 */
public class InfiniteSprayCanBehaviour implements IItemUIFactory, IDurabilityBar, IAddInformation {

    private static final String COLOR_KEY = "SprayColor";
    /** Palette position used for the solvent (strip color) slot; the dyes occupy 0..15. */
    public static final int SOLVENT_SLOT = DyeColor.values().length;
    /** Total selectable slots: every dye plus the solvent. */
    public static final int SLOT_COUNT = SOLVENT_SLOT + 1;
    private static final int SOLVENT_COLOR = 0x969696;

    public static int getColorSlot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(COLOR_KEY, Tag.TAG_INT)) {
            return 0;
        }
        return Mth.clamp(tag.getInt(COLOR_KEY), 0, SOLVENT_SLOT);
    }

    public static void setColorSlot(ItemStack stack, int slot) {
        stack.getOrCreateTag().putInt(COLOR_KEY, Mth.clamp(slot, 0, SOLVENT_SLOT));
    }

    public static void setColorSlot(HeldItemUIFactory.HeldItemHolder holder, int slot) {
        setColorSlot(holder.getHeld(), slot);
        holder.markAsDirty();
    }

    /** @return the selected dye color, or {@code null} when the solvent slot is selected. */
    @Nullable
    public static DyeColor getColor(ItemStack stack) {
        int slot = getColorSlot(stack);
        return slot == SOLVENT_SLOT ? null : DyeColor.values()[slot];
    }

    public static void cycle(ItemStack stack, int direction) {
        setColorSlot(stack, Math.floorMod(getColorSlot(stack) + direction, SLOT_COUNT));
    }

    private static int slotColor(int slot) {
        return slot == SOLVENT_SLOT ? SOLVENT_COLOR : DyeColor.values()[slot].getTextColor();
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        int maxBlocks = player.isShiftKeyDown() ? ConfigHolder.INSTANCE.tools.sprayCanChainLength : 1;
        ColorSprayBehaviour.paintArea(context, getColor(itemStack), maxBlocks, () -> true);
        GTSoundEntries.SPRAY_CAN_TOOL.play(level, null, player.position(), 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        // The palette is opened by middle click, not by right clicking the air.
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        LabelWidget label = new LabelWidget(9, 8, "behaviour.infinite_spray_can.select");
        label.setDropShadow(false);
        label.setTextColor(0x404040);
        var modular = new ModularUI(184, 100, holder, entityPlayer).widget(label);

        int columns = 8;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int finalSlot = slot;
            int col = slot % columns;
            int row = slot / columns;
            modular.widget(new ButtonWidget(10 + (18 * col), 24 + (18 * row), 18, 18,
                    new GuiTextureGroup(GuiTextures.SLOT,
                            new ColorRectTexture(0xFF000000 | slotColor(finalSlot))),
                    data -> setColorSlot(holder, finalSlot)));
        }
        modular.mainGroup.setBackground(GuiTextures.BACKGROUND);
        return modular;
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        int slot = getColorSlot(stack);
        Component colorName = slot == SOLVENT_SLOT ?
                Component.translatable("behaviour.infinite_spray_can.solvent") :
                Component.translatable("color.minecraft." + DyeColor.values()[slot].getSerializedName());
        tooltipComponents.add(Component.translatable("behaviour.infinite_spray_can.current", colorName));
        tooltipComponents.add(Component.translatable("behaviour.infinite_spray_can.left"));
        tooltipComponents.add(Component.translatable("behaviour.infinite_spray_can.middle"));
        tooltipComponents.add(Component.translatable("behaviour.infinite_spray_can.right"));
    }

    // durability bar doubles as a persistent indicator of the currently selected color

    @Override
    public float getDurabilityForDisplay(ItemStack stack) {
        return 1.0f;
    }

    @Override
    public int getMaxDurability(ItemStack stack) {
        return 1;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF000000 | slotColor(getColorSlot(stack));
    }

    @Nullable
    @Override
    public IntIntPair getDurabilityColorsForDisplay(ItemStack itemStack) {
        int color = slotColor(getColorSlot(itemStack));
        return IntIntPair.of(color, color);
    }

    @Override
    public boolean doDamagedStateColors(ItemStack itemStack) {
        return false;
    }
}
