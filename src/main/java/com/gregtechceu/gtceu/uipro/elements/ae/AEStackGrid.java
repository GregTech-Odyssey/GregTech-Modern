package com.gregtechceu.gtceu.uipro.elements.ae;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib.gui.ingredient.IIngredientSlot;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
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
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.api.stack.EmiStackInteraction;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AE 物品 / 流体的只读展示网格（如 ME 输出总线、输出仓的"等待输出"列表），每行 {@link UISizes#SLOTS_PER_ROW} 格，
 * 与其他槽位行对齐：物品格用 {@link UITheme#ITEM_SLOT}、流体格用 {@link UITheme#FLUID_SLOT}，右下角是缩写数量，
 * 悬停显示名称与精确数量，可在 EMI 里查看。取代 GTM 的 {@code AEListGridWidget}（一行一个条目、每 tick 都下发一次）。
 * <p>
 * 至少显示 {@code minRows} 行（空格子占位，界面不因列表增减跳动），条目多时按行加高；放进 {@code ScrollerView} 限高滚动。
 * 同步：服务端只在内容变化时下发增量（新增 / 数量变化 / 移除），打开界面时下发全量；两端控件树不变（只有一个控件），
 * 高度只在客户端随内容变化。
 */
public class AEStackGrid extends Widget implements ILayoutItem, IIngredientSlot {

    /// 内容增量的更新 ID（登记见框架说明：ID_BASE-1 列表行、-2 步进器、-3/-4 弹出面板）
    private static final int CONTENT_ID = SyncValueHost.ID_BASE - 5;
    private static final int COLUMNS = UISizes.SLOTS_PER_ROW;
    private static final int CELL = UISizes.SLOT;

    private final KeyStorage list;
    /// 空格子的底图：流体列表用流体槽，其余用物品槽
    private final boolean fluidList;
    private final int minRows;
    private final LayoutStyle layoutStyle;
    /// 服务端：上次下发的内容
    private final Reference2LongOpenHashMap<AEKey> cached = new Reference2LongOpenHashMap<>();
    /// 客户端：显示的内容（按首次出现的顺序）
    private final List<GenericStack> displayList = new ArrayList<>();

    public AEStackGrid(KeyStorage list, boolean fluidList, int minRows) {
        super(0, 0, COLUMNS * CELL, Math.max(1, minRows) * CELL);
        this.list = list;
        this.fluidList = fluidList;
        this.minRows = Math.max(1, minRows);
        this.layoutStyle = LayoutStyle.fixed(COLUMNS * CELL, this.minRows * CELL, () -> UIElement.markLayoutDirty(this));
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

    private static void writeChanges(FriendlyByteBuf buf, Reference2LongOpenHashMap<AEKey> changes) {
        buf.writeVarInt(changes.size());
        changes.reference2LongEntrySet().fastForEach(entry -> {
            AEKey.writeKey(buf, entry.getKey());
            buf.writeVarLong(entry.getLongValue());
        });
    }

    /** 客户端：把增量合进显示列表，并按条目数调整高度。 */
    private void readChanges(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            var key = AEKey.readKey(buf);
            long delta = buf.readVarLong();
            if (key == null) continue;
            boolean found = false;
            for (var it = displayList.listIterator(); it.hasNext();) {
                var stack = it.next();
                if (stack.what().equals(key)) {
                    long amount = stack.amount() + delta;
                    if (amount > 0) it.set(new GenericStack(key, amount));
                    else it.remove();
                    found = true;
                    break;
                }
            }
            if (!found && delta > 0) displayList.add(new GenericStack(key, delta));
        }
        int rows = Math.max(minRows, (displayList.size() + COLUMNS - 1) / COLUMNS);
        if (rows * CELL != layoutStyle.declaredHeight()) layoutStyle.height(rows * CELL);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        cached.clear();
        writeChanges(buffer, collectChanges());
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        readChanges(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        var changes = collectChanges();
        if (!changes.isEmpty()) writeUpdateInfo(CONTENT_ID, buf -> writeChanges(buf, changes));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == CONTENT_ID) readChanges(buffer);
        else super.readUpdateInfo(id, buffer);
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
            if (i == hovered && stack != null) graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawStack(GuiGraphics graphics, GenericStack stack, int x, int y) {
        String amount;
        if (stack.what() instanceof AEItemKey key) {
            DrawerHelper.drawItemStack(graphics, key.toStack(), x, y, -1, null);
            amount = TextFormattingUtil.formatLongToCompactString(stack.amount(), 4);
        } else if (stack.what() instanceof AEFluidKey key) {
            var fluid = new FluidStack(key.getFluid(), GTMath.saturatedCast(stack.amount()), key.getTag());
            DrawerHelper.drawFluidForGui(graphics, FluidHelperImpl.toFluidStack(fluid), stack.amount(), x, y, 16, 16);
            amount = FormattingUtil.formatNumberReadable(stack.amount(), true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
        } else {
            return;
        }
        DrawerHelper.drawStringFixedCorner(graphics, amount, x + 17, y + 17, 0xFFFFFF, true, 0.5f);
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
