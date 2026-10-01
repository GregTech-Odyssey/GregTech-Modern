package com.gregtechceu.gtceu.uipro.elements.ae;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.data.UIEvent;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib.gui.ingredient.IIngredientSlot;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import dev.emi.emi.api.stack.EmiStackInteraction;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AE 物品 / 流体的只读展示网格（如 ME 输出总线、输出仓的"等待输出"列表），每行 {@link UISizes#SLOTS_PER_ROW} 格，
 * 与其他槽位行对齐：物品格用 {@link UITheme#ITEM_SLOT}、流体格用 {@link UITheme#FLUID_SLOT}，右下角是缩写数量，
 * 悬停显示名称与精确数量，可在 EMI 里查看。取代 GTM 的 {@code AEListGridWidget}（一行一个条目、每 tick 都下发一次）。
 * <p>
 * 至少显示 {@code minRows} 行（空格子占位，界面不因列表增减跳动），条目多时按行加高；放进 {@code ScrollerView} 限高滚动。
 * 同步：服务端只在内容变化时下发增量（新增 / 数量变化 / 移除），打开界面时下发全量；两端控件树不变（只有一个控件），
 * 高度只在客户端随内容变化。
 */
public class AEStackGrid extends Widget implements ILayoutItem, IIngredientSlot, UIChannel.Host {

    private static final ByteStreamCodec<List<Change>> CHANGES = ByteStreamCodec.of(AEStackGrid::writeChanges, AEStackGrid::readChangeList);
    private static final int COLUMNS = UISizes.SLOTS_PER_ROW;
    private static final int CELL = UISizes.SLOT_SIZE;

    private final KeyStorage list;
    /// 空格子的底图：流体列表用流体槽，其余用物品槽
    private final boolean fluidList;
    private final int minRows;
    private final LayoutStyle layoutStyle;
    /// 服务端：上次下发的内容
    private final Reference2LongOpenHashMap<AEKey> cached = new Reference2LongOpenHashMap<>();
    /// 客户端：显示的内容（按首次出现的顺序）
    private final List<GenericStack> displayList = new ArrayList<>();
    private final Object2IntOpenHashMap<AEKey> displayIndex = new Object2IntOpenHashMap<>();
    private final UIChannel channel = new UIChannel(this);
    private final UIEvent<List<Change>> content;

    public AEStackGrid(KeyStorage list, boolean fluidList, int minRows) {
        super(0, 0, COLUMNS * CELL, Math.max(1, minRows) * CELL);
        this.list = list;
        this.fluidList = fluidList;
        this.minRows = Math.max(1, minRows);
        this.layoutStyle = LayoutStyle.fixed(COLUMNS * CELL, this.minRows * CELL, () -> UIElement.markLayoutDirty(this));
        displayIndex.defaultReturnValue(-1);
        content = addEvent(CHANGES, this::applyChanges);
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    /** 客户端显示中的第 {@code index} 项，没有时为 null。 */
    @Nullable
    public GenericStack getAt(int index) {
        return index >= 0 && index < displayList.size() ? displayList.get(index) : null;
    }

    // ==================== 同步 ====================

    /** 服务端：与上次下发的内容比较，收集增量（键 → 数量差，移除为负的原数量）。 */
    private Reference2LongOpenHashMap<AEKey> collectChanges() {
        var changes = new Reference2LongOpenHashMap<AEKey>();
        var cachedIt = cached.reference2LongEntrySet().fastIterator();
        while (cachedIt.hasNext()) {
            var entry = cachedIt.next();
            if (!list.storage.containsKey(entry.getKey())) {
                changes.put(entry.getKey(), -entry.getLongValue());
                cachedIt.remove();
            }
        }
        for (var entry : list) {
            var key = entry.getKey();
            long value = entry.getLongValue();
            long old = cached.getLong(key);
            if (old != value) {
                changes.put(key, value - old);
                cached.put(key, value);
            }
        }
        return changes;
    }

    private record Change(AEKey key, long delta) {}

    private static List<Change> toList(Reference2LongOpenHashMap<AEKey> changes) {
        var result = new ArrayList<Change>(changes.size());
        changes.reference2LongEntrySet().fastForEach(entry -> result.add(new Change(entry.getKey(), entry.getLongValue())));
        return result;
    }

    private static void writeChanges(FriendlyByteBuf buf, List<Change> changes) {
        buf.writeVarInt(changes.size());
        for (var change : changes) {
            AEKey.writeKey(buf, change.key());
            buf.writeVarLong(change.delta());
        }
    }

    private static List<Change> readChangeList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var result = new ArrayList<Change>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            var key = AEKey.readKey(buf);
            long delta = buf.readVarLong();
            if (key != null) result.add(new Change(key, delta));
        }
        return result;
    }

    private void applyChanges(List<Change> changes) {
        boolean removed = false;
        for (var change : changes) {
            var key = change.key();
            long delta = change.delta();
            int index = displayIndex.getInt(key);
            if (index >= 0) {
                long amount = displayList.get(index).amount() + delta;
                if (amount > 0) {
                    displayList.set(index, new GenericStack(key, amount));
                } else {
                    displayList.set(index, null);
                    displayIndex.removeInt(key);
                    removed = true;
                }
            } else if (delta > 0) {
                displayIndex.put(key, displayList.size());
                displayList.add(new GenericStack(key, delta));
            }
        }
        if (removed) {
            displayList.removeIf(Objects::isNull);
            displayIndex.clear();
            for (int i = 0; i < displayList.size(); i++) displayIndex.put(displayList.get(i).what(), i);
        }
        int rows = Math.max(minRows, (displayList.size() + COLUMNS - 1) / COLUMNS);
        if (rows * CELL != layoutStyle.getDeclaredHeight()) layoutStyle.height(rows * CELL);
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        channel.writeInitialData(buffer);
        cached.clear();
        writeChanges(buffer, toList(collectChanges()));
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        channel.readInitialData(buffer);
        applyChanges(readChangeList(buffer));
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
        var changes = collectChanges();
        if (!changes.isEmpty()) content.send(toList(changes));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (!channel.handleClientAction(id, buffer)) super.handleClientAction(id, buffer);
    }

    // ==================== 绘制 ====================

    /** 鼠标所在格的序号，不在网格上时为 -1。 */
    private int cellAt(double mouseX, double mouseY) {
        if (!isMouseOverElement(mouseX, mouseY)) return -1;
        int column = (int) (mouseX - getPositionX()) / CELL, row = (int) (mouseY - getPositionY()) / CELL;
        return column >= COLUMNS ? -1 : row * COLUMNS + column;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int cells = getSizeHeight() / CELL * COLUMNS;
        int hovered = cellAt(mouseX, mouseY);
        for (int i = 0; i < cells; i++) {
            int x = getPositionX() + i % COLUMNS * CELL, y = getPositionY() + i / COLUMNS * CELL;
            var stack = getAt(i);
            boolean fluid = stack == null ? fluidList : stack.what() instanceof AEFluidKey;
            (fluid ? UITheme.FLUID_SLOT : UITheme.ITEM_SLOT).draw(graphics, mouseX, mouseY, x, y, CELL, CELL);
            if (stack != null) drawStack(graphics, stack, x + 1, y + 1);
            if (i == hovered && stack != null) UIDraw.hoverOverlay(graphics, x, y, CELL, CELL);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawStack(GuiGraphics graphics, GenericStack stack, int x, int y) {
        if (stack.what() instanceof AEItemKey key) {
            DrawerHelper.drawItemStack(graphics, key.toStack(), x, y, -1, null);
        } else if (stack.what() instanceof AEFluidKey key) {
            var fluid = new FluidStack(key.getFluid(), GTMath.saturatedCast(stack.amount()), key.getTag());
            DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(fluid), stack.amount(), x, y, 16, 16);
        } else {
            return;
        }
        UIText.drawItemCount(graphics, stack.what().formatAmount(stack.amount(), AmountFormat.SLOT_LARGE_FONT), x, y);
    }

    /** 悬停：名称 + 精确数量（物品带原版物品提示）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var stack = getAt(cellAt(mouseX, mouseY));
        if (stack == null || gui == null || getHoverElement(mouseX, mouseY) != this) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        var tooltips = new ArrayList<Component>();
        var itemStack = ItemStack.EMPTY;
        if (stack.what() instanceof AEItemKey key) {
            itemStack = key.toStack();
            tooltips.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack));
            tooltips.add(Component.literal(String.format("x%,d", stack.amount())));
        } else {
            tooltips.add(stack.what().getDisplayName());
            tooltips.add(Component.literal(String.format("%,d mB", stack.amount())));
        }
        gui.getModularUIGui().setHoverTooltip(tooltips, itemStack, null, itemStack.getTooltipImage().orElse(null));
    }

    /** EMI 查看鼠标下的物品 / 流体。 */
    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var stack = getAt(cellAt(mouseX, mouseY));
        if (stack == null || !GTCEu.Mods.isEMILoaded()) return null;
        var emiStack = EmiStackHelper.toEmiStack(stack);
        return emiStack == null ? null : new EmiStackInteraction(emiStack, null, false);
    }
}
