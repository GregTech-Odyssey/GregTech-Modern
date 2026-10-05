package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.UIIngredient;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 多方块运行中配方的输入 / 输出清单：每行"图标 名称 …… 数量 · 速率"，数量与速率按 k/M/G 缩写，悬停看精确值。
 * 整张清单在配方对象变化时由服务端下发一次，客户端自己排版绘制，控件树不随行数变化。
 */
public class RecipeIOList extends UIElement {

    public static final String INPUTS = "gtceu.gui.recipe_io.inputs";
    public static final String OUTPUTS = "gtceu.gui.recipe_io.outputs";
    public static final String NONE = "gtceu.gui.recipe_io.none";
    public static final String NOT_CONSUMED = "gtceu.gui.recipe_io.not_consumed";
    public static final String PER_SECOND = "gtceu.gui.recipe_io.per_second";
    public static final String CHANCE = "gtceu.gui.recipe_io.chance";
    public static final String EXACT = "gtceu.gui.recipe_io.exact";

    private static final int ROW = UISizes.STATUS_LINE_ICON_HEIGHT;
    private static final int HEADER = UISizes.STATUS_LINE_HEIGHT;
    private static final int ICON = 16;
    private static final int SLOT = 18;
    private static final int TOP = 4;
    private static final int SECTION_GAP = 3;
    private static final int FULL_CHANCE = ContentList.MAX_CHANCE;

    public record Entry(boolean output, ItemStack item, FluidStack fluid, long amount, int chance, boolean blocked) {

        boolean isFluid() {
            return !fluid.isEmpty();
        }
    }

    public record Snapshot(int duration, List<Entry> entries) {

        public static final Snapshot EMPTY = new Snapshot(0, Collections.emptyList());
    }

    public static final ByteStreamCodec<Snapshot> CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, Snapshot value) {
            buf.writeVarInt(value.duration);
            buf.writeVarInt(value.entries.size());
            for (var entry : value.entries) {
                buf.writeBoolean(entry.output);
                buf.writeBoolean(entry.isFluid());
                if (entry.isFluid()) {
                    var fluid = entry.fluid.copy();
                    fluid.setAmount(1);
                    fluid.writeToPacket(buf);
                } else {
                    buf.writeItem(entry.item.copyWithCount(1));
                }
                buf.writeVarLong(entry.amount);
                buf.writeVarInt(entry.chance);
                buf.writeBoolean(entry.blocked);
            }
        }

        @Override
        public Snapshot decode(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            int size = buf.readVarInt();
            var list = new ArrayList<Entry>(size);
            for (int i = 0; i < size; i++) {
                boolean output = buf.readBoolean();
                boolean fluid = buf.readBoolean();
                ItemStack item = ItemStack.EMPTY;
                FluidStack fluidStack = FluidStack.EMPTY;
                if (fluid) fluidStack = FluidStack.readFromPacket(buf);
                else item = buf.readItem();
                list.add(new Entry(output, item, fluidStack, buf.readVarLong(), buf.readVarInt(), buf.readBoolean()));
            }
            return new Snapshot(duration, list);
        }
    };

    private static final int TONE_TEXT = 0;
    private static final int TONE_LABEL = 1;
    private static final int TONE_ERROR = 2;

    private record Row(@Nullable Entry entry, int y, int height, String name, String amount, String rate, int tone) {}

    private final RecipeLogic logic;
    private final SyncValue<Snapshot> snapshot;
    @Nullable
    private IntPredicate fluidOutputBlocked;
    private long cachedBlocked;
    @Nullable
    private String blockedKey;
    @Nullable
    private String blockedDetailKey;
    @Nullable
    private GTRecipe cachedRecipe;
    private Snapshot cached = Snapshot.EMPTY;
    private List<Row> rows = Collections.emptyList();
    private int rowsWidth = -1;
    private int rateColumn;

    protected RecipeIOList(RecipeLogic logic) {
        this.logic = logic;
        layout(l -> l.width(LayoutStyle.AUTO).height(TOP + HEADER));
        this.snapshot = addSyncValue(SyncValue.of(this::current, CODEC, Snapshot.EMPTY).onChanged(value -> {
            rowsWidth = -1;
            layout(l -> l.height(heightFor(value)));
        }));
    }

    public static RecipeIOList of(RecipeLogic logic) {
        return new RecipeIOList(logic);
    }

    public RecipeIOList setFluidOutputBlocked(IntPredicate blocked, String shortKey, String detailKey) {
        this.fluidOutputBlocked = blocked;
        this.blockedKey = shortKey;
        this.blockedDetailKey = detailKey;
        return this;
    }

    private static int heightFor(Snapshot value) {
        if (value.entries.isEmpty()) return TOP + HEADER;
        boolean inputs = false, outputs = false;
        for (var entry : value.entries) {
            if (entry.output) outputs = true;
            else inputs = true;
        }
        return TOP + (inputs ? HEADER : 0) + (outputs ? HEADER : 0) + (inputs && outputs ? SECTION_GAP : 0) + value.entries.size() * ROW;
    }

    private Snapshot current() {
        var recipe = logic.isIdle() ? null : logic.getLastRecipe();
        long blocked = recipe == null ? 0 : blockedMask(recipe.fluidOutputs.size());
        if (recipe == cachedRecipe && blocked == cachedBlocked) return cached;
        cachedRecipe = recipe;
        cachedBlocked = blocked;
        if (recipe == null) {
            cached = Snapshot.EMPTY;
            return cached;
        }
        var list = new ArrayList<Entry>(recipe.itemInputs.size() + recipe.fluidInputs.size() + recipe.itemOutputs.size() + recipe.fluidOutputs.size());
        addItems(list, false, recipe.itemInputs, recipe.scale);
        addFluids(list, false, recipe.fluidInputs, recipe.scale, 0);
        addItems(list, true, recipe.itemOutputs, recipe.scale);
        addFluids(list, true, recipe.fluidOutputs, recipe.scale, blocked);
        cached = new Snapshot(recipe.duration, list);
        return cached;
    }

    private long blockedMask(int fluidOutputs) {
        if (fluidOutputBlocked == null) return 0;
        long mask = 0;
        for (int i = 0, n = Math.min(fluidOutputs, Long.SIZE); i < n; i++) {
            if (fluidOutputBlocked.test(i)) mask |= 1L << i;
        }
        return mask;
    }

    private static void addItems(List<Entry> list, boolean output, ContentList contents, long scale) {
        for (int i = 0; i < contents.size(); i++) {
            if (contents.ingredient(i).displayKey() instanceof AEItemKey key) {
                list.add(new Entry(output, Keys.displayStack(key), FluidStack.EMPTY, contents.effective(i, scale), contents.chance(i), false));
            }
        }
    }

    private static void addFluids(List<Entry> list, boolean output, ContentList contents, long scale, long blocked) {
        for (int i = 0; i < contents.size(); i++) {
            if (contents.ingredient(i).displayKey() instanceof AEFluidKey key) {
                list.add(new Entry(output, ItemStack.EMPTY, Keys.displayFluid(key), contents.effective(i, scale), contents.chance(i), i < Long.SIZE && (blocked >>> i & 1) != 0));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void layoutRows(int width) {
        var value = snapshot.getValue();
        var list = new ArrayList<Row>(value.entries.size() + 2);
        int y = TOP;
        if (value.entries.isEmpty()) {
            list.add(new Row(null, TOP, HEADER, Component.translatable(NONE).getString(), "", "", TONE_LABEL));
        }
        int rateWidth = 0;
        var rates = new String[value.entries.size()];
        for (int i = 0; i < rates.length; i++) {
            var entry = value.entries.get(i);
            if (entry.blocked || entry.chance <= 0) continue;
            rates[i] = rateText(entry, value.duration);
            rateWidth = Math.max(rateWidth, UIText.width(rates[i]));
        }
        rateColumn = rateWidth;
        Boolean section = null;
        for (int i = 0; i < rates.length; i++) {
            var entry = value.entries.get(i);
            if (section == null || section != entry.output) {
                if (section != null) y += SECTION_GAP;
                section = entry.output;
                list.add(new Row(null, y, HEADER, Component.translatable(entry.output ? OUTPUTS : INPUTS).getString(), "", "", TONE_TEXT));
                y += HEADER;
            }
            String amount;
            String rate = "";
            int tone = TONE_TEXT;
            if (entry.blocked && blockedKey != null) {
                amount = Component.translatable(blockedKey).getString();
                tone = TONE_ERROR;
            } else if (entry.chance <= 0) {
                amount = Component.translatable(NOT_CONSUMED).getString();
                tone = TONE_LABEL;
            } else {
                amount = amountText(entry);
                rate = rates[i];
            }
            int valueWidth = UIText.width(amount) + (rate.isEmpty() ? 0 : UISizes.TEXT_PADDING + rateWidth);
            String name = entry.isFluid() ? entry.fluid.getDisplayName().getString() : entry.item.getHoverName().getString();
            name = UIText.fit(name, width - SLOT - UISizes.GAP - valueWidth - UISizes.TEXT_PADDING);
            list.add(new Row(entry, y, ROW, name, amount, rate, tone));
            y += ROW;
        }
        rows = list;
        rowsWidth = width;
    }

    private static double expected(Entry entry) {
        return entry.chance >= FULL_CHANCE ? entry.amount : entry.amount * (double) entry.chance / FULL_CHANCE;
    }

    private static String amountText(Entry entry) {
        String prefix = entry.chance < FULL_CHANCE ? "≈" : "";
        if (entry.isFluid()) return prefix + FormattingUtil.formatNumberReadable(expected(entry), true, FormattingUtil.DECIMAL_FORMAT_1F, "B");
        return prefix + FormattingUtil.formatNumberReadable(expected(entry), false, FormattingUtil.DECIMAL_FORMAT_1F, null);
    }

    private static String rateText(Entry entry, int duration) {
        double perSecond = duration <= 0 ? 0 : expected(entry) * 20 / duration;
        var format = perSecond > 0 && perSecond < 1 ? FormattingUtil.DECIMAL_FORMAT_2F : FormattingUtil.DECIMAL_FORMAT_1F;
        return Component.translatable(PER_SECOND, FormattingUtil.formatNumberReadable(perSecond, entry.isFluid(), format, entry.isFluid() ? "B" : null)).getString();
    }

    @OnlyIn(Dist.CLIENT)
    private List<Row> rows() {
        int width = getSizeWidth();
        if (width != rowsWidth) layoutRows(width);
        return rows;
    }

    @Nullable
    private Row rowAt(double mouseX, double mouseY) {
        if (!isMouseOverElement(mouseX, mouseY)) return null;
        int localY = (int) mouseY - getPositionY();
        for (var row : rows) {
            if (row.entry != null && localY >= row.y && localY < row.y + row.height) return row;
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private static int toneColor(int tone) {
        return switch (tone) {
            case TONE_LABEL -> UITheme.SCREEN_LABEL;
            case TONE_ERROR -> UITheme.SCREEN_ERROR;
            default -> UITheme.SCREEN_TEXT;
        };
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX(), top = getPositionY(), width = getSizeWidth();
        var hovered = rowAt(mouseX, mouseY);
        UIDraw.fillRect(graphics, x, top + 1, width, 1, UITheme.SCREEN_TRACK);
        UIDraw.fillRect(graphics, x, top + 2, width, 1, UITheme.SCREEN_DIVIDER);
        for (var row : rows()) {
            int y = top + row.y;
            int textY = UIText.centerY(y, row.height);
            if (row.entry == null) {
                UIText.drawLeft(graphics, row.name, x, textY, row.tone == TONE_LABEL ? UITheme.SCREEN_LABEL : UITheme.SCREEN_HEADER);
                continue;
            }
            var entry = row.entry;
            UIDraw.bevel(graphics, x, y, SLOT, SLOT, UITheme.SCREEN_TRACK, UITheme.SCREEN_DIVIDER, UITheme.SCREEN_TRACK);
            if (entry.isFluid()) {
                var fluid = entry.fluid.copy();
                fluid.setAmount(1000);
                DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(fluid), 1000, x + 1, y + 1, ICON, ICON);
            } else {
                graphics.renderItem(entry.item, x + 1, y + 1);
            }
            if (entry.blocked) UIDraw.disabledHatch(graphics, x, y, SLOT, SLOT);
            if (row == hovered) UIDraw.hoverOverlay(graphics, x, y, SLOT, SLOT);
            UIText.drawLeft(graphics, row.name, x + SLOT + UISizes.GAP, textY, UITheme.SCREEN_TEXT);
            int right = x + width;
            if (!row.rate.isEmpty()) {
                UIText.drawRight(graphics, row.rate, right, textY, entry.output ? UITheme.SCREEN_GOOD : UITheme.SCREEN_LABEL);
                right -= rateColumn + UISizes.TEXT_PADDING;
            }
            UIText.drawRight(graphics, row.amount, right, textY, toneColor(row.tone));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var row = rowAt(mouseX, mouseY);
        if (row == null || row.entry == null || gui == null || gui.getModularUIGui() == null) return;
        var entry = row.entry;
        var tooltips = new ArrayList<Component>();
        var itemStack = ItemStack.EMPTY;
        String exact;
        if (entry.isFluid()) {
            tooltips.add(entry.fluid.getDisplayName());
            exact = FormattingUtil.formatNumbers(entry.amount) + " mB";
        } else {
            itemStack = entry.item;
            tooltips.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack));
            exact = FormattingUtil.formatNumbers(entry.amount);
        }
        tooltips.add(Component.translatable(EXACT, exact).withStyle(ChatFormatting.GRAY));
        if (entry.chance > 0 && entry.chance < FULL_CHANCE) {
            tooltips.add(Component.translatable(CHANCE, FormattingUtil.formatNumbers(entry.chance / 100.0)).withStyle(ChatFormatting.YELLOW));
        }
        if (entry.blocked && blockedDetailKey != null) tooltips.add(Component.translatable(blockedDetailKey).withStyle(ChatFormatting.RED));
        gui.getModularUIGui().setHoverTooltip(tooltips, itemStack, null, itemStack.getTooltipImage().orElse(null));
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var row = rowAt(mouseX, mouseY);
        if (row == null || row.entry == null) return super.getXEIIngredientOverMouse(mouseX, mouseY);
        var entry = row.entry;
        return UIIngredient.of(entry.isFluid() ? AEFluidKey.of(entry.fluid) : AEItemKey.of(entry.item), entry.amount);
    }
}
