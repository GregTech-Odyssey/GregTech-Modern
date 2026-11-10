package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureDiagnosis;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureDiagnosis.Row;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.UIIngredient;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 结构问题清单：每组一张凸起卡片（槽框图标，主体方块与问题类型、数量两行表头，刻线下是明细）；点击卡片在世界中标出位置，已标出的卡片显示为按下。
 */
public class StructureIssueList extends UIElement {

    public static final String LOCATE_HINT = "gtceu.structure.diagnosis.locate_hint";
    private static final Component UNLOCATE_HINT = Component.translatable("gtceu.structure.diagnosis.unlocate_hint");
    private static final Set<String> SUFFIXED = Set.of("gtceu.structure.diagnosis.alternatives", "gtceu.structure.diagnosis.coords_etc");

    private static final int SLOT = UISizes.SLOT_SIZE;
    private static final int LINE = UISizes.STATUS_LINE_HEIGHT;
    private static final int PAD = 2 + UISizes.GAP;
    private static final int HEADER = 2 * LINE;
    private static final int RULE = UISizes.GAP + 2 + UISizes.GAP;
    private static final int LAMP = 6;

    private static final StreamCodec<FriendlyByteBuf, List<Row>> CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, List<Row> value) {
            buf.writeVarInt(value.size());
            for (var row : value) Row.CODEC.encode(buf, row);
        }

        @Override
        public List<Row> decode(FriendlyByteBuf buf) {
            int size = Math.min(buf.readVarInt(), StructureDiagnosis.MAX_ROWS);
            var rows = new ArrayList<Row>(size);
            for (int i = 0; i < size; i++) rows.add(Row.CODEC.decode(buf));
            return Collections.unmodifiableList(rows);
        }
    };

    private record Text(String main, int mainWidth, String suffix) {

        static final Text EMPTY = new Text("", 0, "");
    }

    private record Entry(Row row, int top, int height, ItemStack icon, Text subject, String title, String value, String[] labels,
                         Text[] values, List<Component> tooltip, List<Component> markedTooltip) {}

    private final SyncValue<List<Row>> rows;
    private final BlockPos anchor;
    private final Consumer<Row> onLocate;
    private List<Entry> entries = Collections.emptyList();
    @Nullable
    private Row selected;
    private int entriesWidth = -1;
    private int labelColumn;

    protected StructureIssueList(int width, Supplier<List<Row>> source, BlockPos anchor, Consumer<Row> onLocate) {
        this.anchor = anchor.immutable();
        this.onLocate = onLocate;
        layout(l -> l.width(width).height(0));
        this.rows = addSyncValue(SyncValue.of(source, CODEC, Collections.emptyList()).onChanged(value -> relayout()));
    }

    public static StructureIssueList of(int width, Supplier<List<Row>> source, BlockPos anchor, Consumer<Row> onLocate) {
        return new StructureIssueList(width, source, anchor, onLocate);
    }

    public List<Row> getRows() {
        return rows.getValue();
    }

    private void relayout() {
        entriesWidth = -1;
        var value = getRows();
        layout(l -> l.height(heightFor(value)));
    }

    private static int heightFor(List<Row> value) {
        if (value.isEmpty()) return 0;
        int height = (value.size() - 1) * UISizes.GAP;
        for (var row : value) height += cardHeight(row.lines().size());
        return height;
    }

    private static int cardHeight(int lines) {
        return PAD + HEADER + (lines > 0 ? RULE + lines * LINE : 0) + PAD;
    }

    @OnlyIn(Dist.CLIENT)
    private static Text fit(Component value, int width) {
        if (value.getContents() instanceof TranslatableContents translatable && SUFFIXED.contains(translatable.getKey()) && translatable.getArgs().length > 0 &&
                translatable.getArgs()[0] instanceof Component name) {
            var args = translatable.getArgs().clone();
            args[0] = "";
            String suffix = plain(Component.translatable(translatable.getKey(), args));
            String main = plain(name);
            if (plain(value).equals(main + suffix)) {
                String fitted = UIText.fit(main, width - UIText.width(suffix));
                return new Text(fitted, UIText.width(fitted), suffix);
            }
        }
        String fitted = UIText.fit(plain(value), width);
        return new Text(fitted, UIText.width(fitted), "");
    }

    @OnlyIn(Dist.CLIENT)
    private static void draw(GuiGraphics graphics, Text text, int x, int y) {
        UIText.drawLeft(graphics, text.main, x, y, UITheme.text());
        if (!text.suffix.isEmpty()) UIText.drawLeft(graphics, text.suffix, x + text.mainWidth, y, UITheme.textSecondary());
    }

    private static String plain(Component component) {
        var text = ChatFormatting.stripFormatting(component.getString());
        return text == null ? "" : text;
    }

    @OnlyIn(Dist.CLIENT)
    private List<Entry> entries() {
        int width = getSizeWidth();
        if (width != entriesWidth) layoutEntries(width);
        return entries;
    }

    @OnlyIn(Dist.CLIENT)
    private void layoutEntries(int width) {
        var value = getRows();
        int labels = 0;
        for (var row : value) {
            for (var line : row.lines()) labels = Math.max(labels, UIText.width(plain(line.label())));
        }
        labelColumn = Math.max(SLOT + UISizes.TEXT_PADDING, labels + UISizes.TEXT_PADDING);
        int inner = width - 2 * PAD;
        int text = inner - SLOT - UISizes.TEXT_PADDING;
        var list = new ArrayList<Entry>(value.size());
        int y = 0;
        for (var row : value) {
            String count = plain(row.value());
            int titleWidth = text - (count.isEmpty() ? 0 : UIText.width(count) + UISizes.TEXT_PADDING);
            var subject = row.subject();
            var subjectText = subject == null ? Text.EMPTY : fit(subject.value(), text);
            String title = UIText.fit(plain(row.title()), titleWidth);
            int lines = row.lines().size();
            var lineLabels = new String[lines];
            var lineValues = new Text[lines];
            var tooltip = new ArrayList<Component>(lines + 4);
            tooltip.add(row.title());
            if (subject != null) addTooltip(tooltip, subject);
            for (int i = 0; i < lines; i++) {
                var line = row.lines().get(i);
                lineLabels[i] = plain(line.label());
                lineValues[i] = StructureDiagnosis.POSITION_FREE.equals(line.value()) ? new Text("", 0, UIText.fit(plain(line.value()), inner - labelColumn)) :
                        fit(line.value(), inner - labelColumn);
                addTooltip(tooltip, line);
            }
            var markedTooltip = new ArrayList<>(tooltip);
            if (row.positions().length > 0) {
                tooltip.add(Component.translatable(LOCATE_HINT, row.count()).withStyle(ChatFormatting.DARK_GRAY));
                markedTooltip.add(UNLOCATE_HINT.copy().withStyle(ChatFormatting.DARK_GRAY));
            }
            int height = cardHeight(lines);
            var icon = row.icon() == Items.AIR ? ItemStack.EMPTY : new ItemStack(row.icon());
            list.add(new Entry(row, y, height, icon, subjectText, title, count, lineLabels, lineValues, List.copyOf(tooltip), List.copyOf(markedTooltip)));
            y += height + UISizes.GAP;
        }
        entries = list;
        entriesWidth = width;
    }

    private static void addTooltip(List<Component> tooltip, StructureDiagnosis.Line line) {
        tooltip.add(Component.empty().append(line.label().copy().withStyle(ChatFormatting.GRAY)).append(" ").append(line.value()));
        if (!line.detail().getString().isEmpty()) tooltip.add(line.detail().copy().withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private Entry entryAt(double mouseX, double mouseY) {
        if (!isMouseOverElement(mouseX, mouseY)) return null;
        int localY = (int) mouseY - getPositionY();
        for (var entry : entries) {
            if (localY >= entry.top && localY < entry.top + entry.height) return entry;
        }
        return null;
    }

    private boolean overIcon(Entry entry, double mouseX, double mouseY) {
        int x = getPositionX() + PAD, y = getPositionY() + entry.top + PAD + (HEADER - SLOT) / 2;
        return mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
    }

    @OnlyIn(Dist.CLIENT)
    private boolean isMarked(Entry entry) {
        var positions = entry.row.positions();
        return entry.row == selected && positions.length > 0 && StructureIssueOverlay.isShowing(anchor, positions);
    }

    @OnlyIn(Dist.CLIENT)
    private static int severityText(int severity) {
        return switch (severity) {
            case StructureDiagnosis.ERROR -> UITheme.STATUS_TEXT_ERROR;
            case StructureDiagnosis.WARNING -> UITheme.STATUS_TEXT_WARNING;
            default -> UITheme.textSecondary();
        };
    }

    @OnlyIn(Dist.CLIENT)
    private static int severityLamp(int severity) {
        return switch (severity) {
            case StructureDiagnosis.ERROR -> UITheme.STATUS_OFFLINE;
            case StructureDiagnosis.WARNING -> UITheme.STATUS_WARNING;
            default -> UITheme.textSecondary();
        };
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX(), top = getPositionY(), width = getSizeWidth();
        var hovered = entryAt(mouseX, mouseY);
        for (var entry : entries()) {
            int y = top + entry.top;
            boolean locatable = entry.row.positions().length > 0;
            boolean marked = isMarked(entry);
            (!locatable ? UITheme.CARD_STATIC : marked ? UITheme.CARD_PRESSED : UITheme.CARD).draw(graphics, mouseX, mouseY, x, y, width, entry.height);
            if (locatable && !marked && entry == hovered) UIDraw.fillRect(graphics, x + 2, y + 2, width - 4, entry.height - 4, UITheme.SEGMENT_HOVER);
            int shift = marked ? 1 : 0;
            int sx = x + PAD + shift, sy = y + PAD + shift;
            int slotY = sy + (HEADER - SLOT) / 2;
            UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, sx, slotY, SLOT, SLOT);
            if (!entry.icon.isEmpty()) {
                UIDraw.item(graphics, entry.icon, sx + 1, slotY + 1);
            } else {
                UIDraw.lamp(graphics, sx + (SLOT - LAMP) / 2, slotY + (SLOT - LAMP) / 2, LAMP, severityLamp(entry.row.severity()));
            }
            int textX = sx + SLOT + UISizes.TEXT_PADDING, right = x + width - PAD + shift;
            if (entry.subject.main.isEmpty()) {
                int titleY = UIText.centerY(sy, HEADER);
                UIText.drawLeft(graphics, entry.title, textX, titleY, UITheme.text());
                UIText.drawRight(graphics, entry.value, right, titleY, severityText(entry.row.severity()));
            } else {
                draw(graphics, entry.subject, textX, UIText.centerY(sy, LINE));
                int secondY = UIText.centerY(sy + LINE, LINE);
                UIText.drawLeft(graphics, entry.title, textX, secondY, UITheme.textSecondary());
                UIText.drawRight(graphics, entry.value, right, secondY, severityText(entry.row.severity()));
            }
            if (entry.labels.length == 0) continue;
            UITheme.ETCH.draw(graphics, mouseX, mouseY, sx, sy + HEADER, width - 2 * PAD, RULE);
            int lineTop = sy + HEADER + RULE;
            for (int line = 0; line < entry.labels.length; line++) {
                int lineY = UIText.centerY(lineTop + line * LINE, LINE);
                UIText.drawLeft(graphics, entry.labels[line], sx, lineY, UITheme.textSecondary());
                draw(graphics, entry.values[line], sx + labelColumn, lineY);
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var entry = entryAt(mouseX, mouseY);
        if (entry == null || gui == null || gui.getModularUIGui() == null) return;
        if (!entry.icon.isEmpty() && overIcon(entry, mouseX, mouseY)) {
            gui.getModularUIGui().setHoverTooltip(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.icon), entry.icon, null, null);
            return;
        }
        gui.getModularUIGui().setHoverTooltip(isMarked(entry) ? entry.markedTooltip : entry.tooltip, ItemStack.EMPTY, null, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var entry = button == 0 ? entryAt(mouseX, mouseY) : null;
        if (entry != null && entry.row.positions().length > 0) {
            if (isMarked(entry)) {
                selected = null;
                StructureIssueOverlay.clear();
            } else {
                selected = entry.row;
                onLocate.accept(entry.row);
            }
            playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var entry = entryAt(mouseX, mouseY);
        if (entry != null && !entry.icon.isEmpty() && overIcon(entry, mouseX, mouseY)) return UIIngredient.of(entry.icon);
        return super.getXEIIngredientOverMouse(mouseX, mouseY);
    }
}
