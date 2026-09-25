package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.ClientActions;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 状态行：机器状态显示的一行，与一行文字同高（{@link #HEIGHT}）。
 *
 * <pre>
 * 名称 ………………………………… 数值          （{@link Level#NORMAL}：纯信息，没有任何标记）
 * 名称 ……………………………… ● 数值          （其他等级：数值前一颗状态灯，灯与数值同色：正常绿、注意黄、错误红）
 * </pre>
 *
 * 名称用次要文字色，数值右对齐；名称为空时整行是一句状态（有灯时灯在句首）。文字放不下时截断加省略号。
 * <p>
 * 状态行<strong>不用</strong> {@link InfoIcon}：信息图标的含义是"悬停看说明"，放在每一行会让玩家分不清哪里能悬停，
 * 行也会被撑高。需要解释的行用 {@link #detail}（服务端下发的悬停说明），没有说明时悬停只在文字被截断时显示全文。
 * <p>
 * 数值、等级、说明都由服务端取值下发（{@link SyncValue}）；构造时<strong>不会</strong>调用 getter，可以依赖服务端独有的数据。
 * 通常放进 {@link StatusPanel} 里纵向堆叠，而不是单独散放。
 * <p>
 * 可选：{@link #icon} 在数值左边显示对应物品的图标（行高加到 {@link #ICON_HEIGHT}，悬停看物品提示、可交给 EMI 查配方）；
 * {@link #onClick} 让数值可以点击（悬停时数值加下划线、提示里写明点击做什么），点击由服务端执行。
 */
public class StatusLine extends UIElement {

    public static final int HEIGHT = UISizes.STATUS_LINE_HEIGHT;
    /** 带物品图标的行高。 */
    public static final int ICON_HEIGHT = UISizes.STATUS_LINE_ICON_HEIGHT;
    /// 状态灯边长（含 1 像素深色描边）与灯和数值之间的空隙
    private static final int LAMP = 6;
    private static final int LAMP_GAP = 3;
    /// 物品图标边长与图标和数值之间的空隙
    private static final int ICON = 16;
    private static final int ICON_GAP = 2;
    /// 点击的客户端请求（SyncValueHost 段以下的登记见 Adjuster）
    private static final int CLICK_ID = SyncValueHost.ID_BASE - 10;

    /** 这一项的好坏。{@link #NORMAL} 是纯信息，不画灯、不上色。 */
    public enum Level {

        NORMAL(0, UITheme.TEXT),
        GOOD(UITheme.STATUS_ONLINE, UITheme.STATUS_TEXT_GOOD),
        WARNING(UITheme.STATUS_WARNING, UITheme.STATUS_TEXT_WARNING),
        ERROR(UITheme.STATUS_OFFLINE, UITheme.STATUS_TEXT_ERROR);

        private final int lampColor;
        private final int textColor;

        Level(int lampColor, int textColor) {
            this.lampColor = lampColor;
            this.textColor = textColor;
        }

        public boolean hasLamp() {
            return this != NORMAL;
        }

        public int textColor() {
            return textColor;
        }
    }

    private static final Level[] LEVELS = Level.values();

    private final Component label;
    private final SyncValue<Component> value;
    private final SyncValue<Integer> level;
    private Supplier<Level> levelGetter = () -> Level.NORMAL;
    @Nullable
    private SyncValue<Component> detail;
    @Nullable
    private SyncValue<SyncItem> icon;
    /// 可点击：服务端判定能否点击（下发）、点击时（服务端）做什么、提示里的说明
    @Nullable
    private SyncValue<Boolean> clickable;
    @Nullable
    private Consumer<Player> onClick;
    @Nullable
    private Component clickHint;
    /// 本帧图标的横坐标（悬停提示、EMI 查询用），没有图标时为 MIN_VALUE
    private int iconX = Integer.MIN_VALUE;

    public StatusLine(int width, Component label, Supplier<Component> value) {
        this.label = label;
        layout(l -> l.size(width, HEIGHT));
        this.value = addSyncValue(SyncValue.of(value, SyncValue.COMPONENT, Component.empty()));
        this.level = addSyncValue(SyncValue.ofInt(() -> levelGetter.get().ordinal(), Level.NORMAL.ordinal()));
    }

    /** 名称取翻译键的一行，默认 {@link Level#NORMAL}。 */
    public static StatusLine of(int width, String labelKey, Supplier<Component> value) {
        return new StatusLine(width, Component.translatable(labelKey), value);
    }

    /** 没有名称、整行一句话的状态。 */
    public static StatusLine sentence(int width, Supplier<Component> text) {
        return new StatusLine(width, Component.empty(), text);
    }

    /** 等级随状态变化（服务端取值下发）。只有表示好坏的项才设，纯信息项保持默认。 */
    public StatusLine level(Supplier<Level> level) {
        this.levelGetter = level;
        return this;
    }

    /**
     * 悬停说明（服务端取值下发），例如行内显示短句"未绑定所有者"，悬停看完整原因。
     * 两端建页时都要以同样顺序调用（它会注册一个同步值）。
     */
    public StatusLine detail(Supplier<Component> detail) {
        this.detail = addSyncValue(SyncValue.of(detail, SyncValue.COMPONENT, Component.empty()));
        return this;
    }

    /**
     * 数值左边显示物品图标（服务端取值下发，空物品不显示），行高随之加到 {@link #ICON_HEIGHT}。
     * getter 最好返回缓存的物品（同一对象时不再比较内容）。两端建页时都要以同样顺序调用。
     */
    public StatusLine icon(Supplier<ItemStack> icon) {
        var last = new ItemStack[1];
        var memo = new SyncItem[] { SyncItem.EMPTY };
        this.icon = addSyncValue(SyncValue.of(() -> {
            var stack = icon.get();
            if (stack != last[0]) {
                last[0] = stack;
                memo[0] = SyncItem.of(stack);
            }
            return memo[0];
        }, SyncItem.CODEC, SyncItem.EMPTY));
        layout(l -> l.height(ICON_HEIGHT));
        return this;
    }

    /**
     * 数值可以点击：{@code enabled}（服务端取值下发）为真时悬停显示下划线，提示末尾加上 {@code hint}；
     * 点击后服务端再检查一次 {@code enabled}，再对打开界面的玩家执行 {@code action}。两端建页时都要以同样顺序调用。
     */
    public StatusLine onClick(Component hint, BooleanSupplier enabled, Consumer<Player> action) {
        this.clickable = addSyncValue(SyncValue.of(enabled::getAsBoolean, SyncValue.BOOLEAN, false));
        this.onClick = player -> {
            if (enabled.getAsBoolean()) action.accept(player);
        };
        this.clickHint = hint;
        return this;
    }

    private boolean isClickable() {
        return clickable != null && clickable.getValue();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isClickable() && isMouseOverElement(mouseX, mouseY)) {
            if (!ClientActions.handleLocally(this, CLICK_ID, buf -> {})) writeClientAction(CLICK_ID, buf -> {});
            playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == CLICK_ID) {
            if (onClick != null && gui != null && gui.entityPlayer != null) onClick.accept(gui.entityPlayer);
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    /** 鼠标在图标上时交给 EMI（查配方 / 用途）。 */
    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var stack = iconAt(mouseX, mouseY);
        return stack != null ? stack : super.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    @Nullable
    private ItemStack iconAt(double mouseX, double mouseY) {
        if (icon == null || iconX == Integer.MIN_VALUE || icon.getValue().stack().isEmpty()) return null;
        int top = getPositionY() + (getSizeHeight() - ICON) / 2;
        return isMouseOver(iconX, top, ICON, ICON, mouseX, mouseY) ? icon.getValue().stack() : null;
    }

    /** 固定的悬停说明（翻译键）。 */
    public StatusLine tooltip(String... translationKeys) {
        var tooltips = new Component[translationKeys.length];
        for (int i = 0; i < translationKeys.length; i++) tooltips[i] = Component.translatable(translationKeys[i]);
        setHoverTooltips(tooltips);
        return this;
    }

    /** 最近一次同步到的数值。 */
    public Component getValue() {
        return value.getValue();
    }

    /** 最近一次同步到的等级。 */
    public Level getLevel() {
        int index = level.getValue();
        return index >= 0 && index < LEVELS.length ? LEVELS[index] : Level.NORMAL;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
        var font = Minecraft.getInstance().font;
        var current = getLevel();
        int textY = y + (height - 8) / 2;
        var stack = icon == null ? ItemStack.EMPTY : icon.getValue().stack();
        // 数值前面依次是灯、物品图标
        int iconSpace = stack.isEmpty() ? 0 : ICON + ICON_GAP;
        int lampSpace = (current.hasLamp() ? LAMP + LAMP_GAP : 0) + iconSpace;
        String valueText = value.getValue().getString();
        String labelText = label.getString();
        boolean underline = isClickable() && isMouseOverElement(mouseX, mouseY);
        int valueX;
        String shownValue;
        if (labelText.isEmpty()) {
            shownValue = UITheme.clip(font, valueText, width - lampSpace);
            valueX = x + lampSpace;
        } else {
            // 数值（连同灯、图标）优先完整显示，但最多占一半；名称用剩下的宽度
            int valueWidth = Math.min(font.width(valueText) + lampSpace, width / 2);
            String shownLabel = UITheme.clip(font, labelText, width - valueWidth - UISizes.TEXT_PADDING);
            graphics.drawString(font, shownLabel, x, textY, UITheme.TEXT_SECONDARY, false);
            shownValue = UITheme.clip(font, valueText, width - font.width(shownLabel) - UISizes.TEXT_PADDING - lampSpace);
            valueX = x + width - font.width(shownValue);
        }
        graphics.drawString(font, shownValue, valueX, textY, current.textColor, false);
        if (underline) graphics.fill(valueX, textY + 9, valueX + font.width(shownValue), textY + 10, current.textColor);
        iconX = stack.isEmpty() ? Integer.MIN_VALUE : valueX - iconSpace;
        if (!stack.isEmpty()) graphics.renderItem(stack, iconX, y + (height - ICON) / 2);
        if (current.hasLamp()) drawLamp(graphics, valueX - lampSpace, y, height, current);
    }

    /** 状态灯：1 像素深色描边的小方块，左上一点高光。 */
    @OnlyIn(Dist.CLIENT)
    private static void drawLamp(GuiGraphics graphics, int x, int y, int height, Level level) {
        int top = y + (height - LAMP) / 2;
        graphics.fill(x, top, x + LAMP, top + LAMP, UITheme.STATUS_LAMP_OUTLINE);
        graphics.fill(x + 1, top + 1, x + LAMP - 1, top + LAMP - 1, level.lampColor);
        graphics.fill(x + 1, top + 1, x + 2, top + 2, 0x80FFFFFF);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        // 图标上显示物品提示；否则固定说明优先，其次服务端下发的说明（可点击时末尾加上点击说明）；都没有且文字被截断时，悬停显示全文
        if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        var hoveredIcon = iconAt(mouseX, mouseY);
        if (hoveredIcon != null) {
            gui.getModularUIGui().setHoverTooltip(Screen.getTooltipFromItem(Minecraft.getInstance(), hoveredIcon), hoveredIcon, null, null);
            return;
        }
        if (!tooltipTexts.isEmpty()) return;
        boolean hasDetail = detail != null && !detail.getValue().getString().isEmpty();
        if (hasDetail || (isClickable() && clickHint != null)) {
            var lines = new ArrayList<Component>(2);
            if (hasDetail) lines.add(detail.getValue());
            if (isClickable() && clickHint != null) lines.add(clickHint);
            gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
            return;
        }
        var font = Minecraft.getInstance().font;
        String labelText = label.getString(), valueText = value.getValue().getString();
        int lampSpace = (getLevel().hasLamp() ? LAMP + LAMP_GAP : 0) + (iconX == Integer.MIN_VALUE ? 0 : ICON + ICON_GAP);
        int gap = labelText.isEmpty() ? 0 : UISizes.TEXT_PADDING;
        if (font.width(labelText) + gap + lampSpace + font.width(valueText) > getSizeWidth()) {
            var full = labelText.isEmpty() ? value.getValue() : label.copy().append(" ").append(value.getValue());
            gui.getModularUIGui().setHoverTooltip(List.of(full), ItemStack.EMPTY, null, null);
        }
    }
}
