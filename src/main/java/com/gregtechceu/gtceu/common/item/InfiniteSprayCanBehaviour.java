package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IDurabilityBar;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.SlotButton;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

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
 * <p>
 * Controls (AE Color Applicator–style for wheel):
 * <ul>
 * <li>Right-click block — paint</li>
 * <li>Right-click air — open the color palette GUI</li>
 * <li>Sneak + scroll — cycle selected color (client intercept → server packet)</li>
 * </ul>
 */
public class InfiniteSprayCanBehaviour implements IItemUIFactory, IDurabilityBar, IAddInformation {

    private static final String COLOR_KEY = "SprayColor";
    /** Palette position used for the solvent (strip color) slot; the dyes occupy 0..15. */
    public static final int SOLVENT_SLOT = DyeColor.values().length;
    /** Total selectable slots: every dye plus the solvent. */
    public static final int SLOT_COUNT = SOLVENT_SLOT + 1;
    private static final int SOLVENT_COLOR = 0x969696;
    private static final String CURRENT_COLOR = "behaviour.infinite_spray_can.current_color";

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

    /** GT5-style shake when the selected color changes (scroll or palette). */
    public static void playColorSwitchSound(Player player) {
        if (player == null || player.level() == null) {
            return;
        }
        var level = player.level();
        if (level.isClientSide) {
            GTSoundEntries.SPRAY_CAN_SHAKE.playAt(level, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f,
                    false);
        } else {
            // Exclude the cycling player so they don't double-hear when client already played.
            GTSoundEntries.SPRAY_CAN_SHAKE.play(level, player, player.position(), 1.0f, 1.0f);
        }
    }

    private static int slotColor(int slot) {
        return slot == SOLVENT_SLOT ? SOLVENT_COLOR : DyeColor.byId(slot).getTextColor();
    }

    private static Component colorName(int slot) {
        if (slot == SOLVENT_SLOT) {
            return Component.translatable("behaviour.infinite_spray_can.solvent");
        }
        return Component.translatable("color.minecraft." + DyeColor.byId(slot).getSerializedName());
    }

    private static ItemStack slotStack(int slot) {
        return slot == SOLVENT_SLOT ? GTItems.SPRAY_SOLVENT.asStack() : GTItems.SPRAY_CAN_DYES[slot].asStack();
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // Right-click block: paint. Palette opens via use() when targeting air.
        // maxBlocks=1 by default; GTOCore redirects this when FTB Ultimine is held.
        ColorSprayBehaviour.paintArea(context, getColor(itemStack), 1, () -> true);
        GTSoundEntries.SPRAY_CAN_TOOL.play(level, null, player.position(), 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        if (player.isShiftKeyDown()) return IItemUIFactory.super.use(item, level, player, usedHand);
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        return new HeldItemPage(holder, window -> {
            var stacks = new ItemStack[SLOT_COUNT];
            for (int slot = 0; slot < SLOT_COUNT; slot++) stacks[slot] = slotStack(slot);
            var status = new StatusPanel(LayoutStyle.AUTO);
            status.addLine(CURRENT_COLOR, () -> colorName(getColorSlot(holder.getHeld())))
                    .icon(() -> stacks[getColorSlot(holder.getHeld())]);
            var palette = UIElement.column(UISizes.SLOT_ROW_WIDTH);
            var current = palette.addSyncValue(SyncValue.ofInt(() -> getColorSlot(holder.getHeld()), 0));
            for (int rowStart = 0; rowStart < SLOT_COUNT; rowStart += UISizes.SLOTS_PER_ROW) {
                var row = UIElement.row(UISizes.SLOT);
                for (int slot = rowStart; slot < Math.min(SLOT_COUNT, rowStart + UISizes.SLOTS_PER_ROW); slot++) {
                    int color = slot;
                    var cell = SlotButton.of(new ItemStackTexture(stacks[color]))
                            .setSelected(() -> current.getValue() == color)
                            .setOnServerClick(() -> {
                                if (getColorSlot(holder.getHeld()) == color) return;
                                setColorSlot(holder, color);
                                GTSoundEntries.SPRAY_CAN_SHAKE.play(entityPlayer.level(), null, entityPlayer.position(), 1.0f, 1.0f);
                            });
                    if (color == SOLVENT_SLOT) {
                        cell.setHoverTooltips(colorName(color), Component.translatable("behaviour.infinite_spray_can.solvent_hint"));
                    } else {
                        cell.setHoverTooltips(colorName(color));
                    }
                    row.addChild(cell);
                }
                palette.addChild(row);
            }
            return UIElement.column(LayoutStyle.AUTO).layout(l -> l.minWidth(UISizes.CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP))
                    .addChildren(status, palette);
        }).noInventory().createUI(entityPlayer);
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        // Tooltips are provided by GTOCore (single “妙妙工具” line); keep GTM silent.
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
        int color = 0xFF000000 | slotColor(getColorSlot(itemStack));
        return IntIntPair.of(color, color);
    }

    @Override
    public boolean doDamagedStateColors(ItemStack itemStack) {
        return false;
    }
}
