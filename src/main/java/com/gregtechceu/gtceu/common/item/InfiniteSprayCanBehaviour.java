package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IDurabilityBar;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>Right-click block — paint</li>
 *   <li>Right-click air — open the color palette GUI</li>
 *   <li>Sneak + scroll — cycle selected color (client intercept → server packet)</li>
 * </ul>
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

    /** Texture for a palette cell: slot chrome + spray-can / solvent icon (+ selection border). */
    private static IGuiTexture paletteCellTexture(int slot, boolean selected) {
        IGuiTexture chrome = selected ? GuiTextures.SLOT_DARK : GuiTextures.SLOT;
        ItemStack icon = slot == SOLVENT_SLOT ?
                GTItems.SPRAY_SOLVENT.asStack() :
                GTItems.SPRAY_CAN_DYES[slot].asStack();
        IGuiTexture item = new ItemStackTexture(icon).scale(16f / 18f);
        if (selected) {
            // gold border marks the active color
            return new GuiTextureGroup(chrome, item, new ColorBorderTexture(1, 0xFFFFD700));
        }
        return new GuiTextureGroup(chrome, item);
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
        // Right-click air (or when block interaction was not consumed): open palette.
        if (player instanceof ServerPlayer serverPlayer) {
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, usedHand);
        }
        return InteractionResultHolder.success(player.getItemInHand(usedHand));
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        // Layout:
        //  title + current color | color preview (top-right)
        //  4×4 dye grid
        //  solvent cell alone on the bottom row, anchored bottom-right
        final int pad = 12;
        final int uiW = 176;
        final int grid = 4;
        final int cell = 18;
        final int gridX = pad;
        final int gridY = 36;
        final int gridBottom = gridY + grid * cell; // exclusive bottom of dye grid
        final int solventY = gridBottom + 6;
        final int uiH = solventY + cell + pad;
        final int previewSize = 24;
        final int previewX = uiW - pad - previewSize;
        final int previewY = 8;

        LabelWidget title = new LabelWidget(8, 6, "behaviour.infinite_spray_can.select");
        title.setDropShadow(false);
        title.setTextColor(0x404040);

        LabelWidget currentLabel = new LabelWidget(8, 20, () -> Component
                .translatable("behaviour.infinite_spray_can.current", colorName(getColorSlot(holder.getHeld())))
                .getString());
        currentLabel.setDropShadow(false);
        currentLabel.setTextColor(0x505050);

        ImageWidget preview = new ImageWidget(previewX, previewY, previewSize, previewSize,
                () -> previewTexture(getColorSlot(holder.getHeld())));
        preview.setHoverTooltips(Component.translatable("behaviour.infinite_spray_can.preview_hint"));

        var modular = new ModularUI(uiW, uiH, holder, entityPlayer)
                .widget(title)
                .widget(currentLabel)
                .widget(preview);

        ButtonWidget[] cells = new ButtonWidget[SLOT_COUNT];

        Runnable refreshSelection = () -> {
            int selected = getColorSlot(holder.getHeld());
            for (int s = 0; s < SLOT_COUNT; s++) {
                if (cells[s] != null) {
                    cells[s].setButtonTexture(paletteCellTexture(s, s == selected));
                }
            }
        };

        // 4×4 dye grid
        for (int slot = 0; slot < SOLVENT_SLOT; slot++) {
            int finalSlot = slot;
            int col = slot % grid;
            int row = slot / grid;
            int x = gridX + col * cell;
            int y = gridY + row * cell;
            boolean selected = getColorSlot(holder.getHeld()) == finalSlot;
            ButtonWidget btn = new ButtonWidget(x, y, cell, cell,
                    paletteCellTexture(finalSlot, selected),
                    data -> {
                        if (getColorSlot(holder.getHeld()) != finalSlot) {
                            setColorSlot(holder, finalSlot);
                            playColorSwitchSound(entityPlayer);
                            refreshSelection.run();
                        }
                    });
            btn.setHoverBorderTexture(1, 0xFFFFFFFF);
            btn.setHoverTooltips(colorName(finalSlot));
            cells[slot] = btn;
            modular.widget(btn);
        }

        // Solvent — bottom-right of the panel (below the dye grid)
        final int solventX = uiW - pad - cell;
        boolean solventSelected = getColorSlot(holder.getHeld()) == SOLVENT_SLOT;
        ButtonWidget solventBtn = new ButtonWidget(solventX, solventY, cell, cell,
                paletteCellTexture(SOLVENT_SLOT, solventSelected),
                data -> {
                    if (getColorSlot(holder.getHeld()) != SOLVENT_SLOT) {
                        setColorSlot(holder, SOLVENT_SLOT);
                        playColorSwitchSound(entityPlayer);
                        refreshSelection.run();
                    }
                });
        solventBtn.setHoverBorderTexture(1, 0xFFFFFFFF);
        solventBtn.setHoverTooltips(
                Component.translatable("behaviour.infinite_spray_can.solvent"),
                Component.translatable("behaviour.infinite_spray_can.solvent_hint"));
        cells[SOLVENT_SLOT] = solventBtn;
        modular.widget(solventBtn);

        // Short label just left of the solvent cell (right-aligned block)
        LabelWidget solventLabel = new LabelWidget(solventX - 28, solventY + 5,
                "behaviour.infinite_spray_can.solvent");
        solventLabel.setDropShadow(false);
        solventLabel.setTextColor(0x404040);
        modular.widget(solventLabel);

        modular.mainGroup.setBackground(GuiTextures.BACKGROUND);
        return modular;
    }

    private static IGuiTexture previewTexture(int slot) {
        int rgb = 0xFF000000 | slotColor(slot);
        return new GuiTextureGroup(
                GuiTextures.SLOT,
                new ColorRectTexture(rgb),
                new ColorBorderTexture(1, 0xFF2C3335));
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
