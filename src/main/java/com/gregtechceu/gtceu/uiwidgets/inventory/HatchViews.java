package com.gregtechceu.gtceu.uiwidgets.inventory;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeMachinePage;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 输入/输出总线、输入/输出仓、输入/输出总成及单格储罐/箱等存储部件的主页布局：
 * 页面与单方块配方机器同一标准高度（内容更高才撑高）；槽位网格超过 {@link #MAX_GRID_ROWS} 行时放进滚动区；
 * 输出方向的槽只能取出、不能放入（与原 GTM 界面一致）。
 */
public final class HatchViews {

    private HatchViews() {}

    /** "存储数量"（超级箱等）。 */
    public static final Component STORED = Component.translatable("gtceu.gui.hatch.stored");
    /** "取出一组"按钮说明。 */
    public static final Component EXTRACT_STACK = Component.translatable("gtceu.gui.hatch.extract_stack");
    public static final Component CAPACITY_LIMIT = Component.translatable("gtceu.gui.hatch.capacity_limit");
    public static final Component CAPACITY_LIMIT_TIP = Component.translatable("gtceu.gui.hatch.capacity_limit.tooltip");
    private static final Component LOCK_ON = Component.translatable("gtceu.gui.hatch.lock_on");
    private static final Component LOCK_OFF = Component.translatable("gtceu.gui.hatch.lock_off");
    private static final Component LOCK_NEED_EMPTY = Component.translatable("gtceu.gui.hatch.lock_need_empty");

    /** 槽位网格最多直接显示的行数，超出放进滚动区（与多方块状态显示窗同高）。 */
    public static final int MAX_GRID_ROWS = UISizes.MACHINE_PAGE_HEIGHT / UISizes.SLOT_SIZE;
    /** 操作区里不同组控件之间的间距（如储罐槽与锁定开关之间）。 */
    public static final int GROUP_GAP = 2 * UISizes.SECTION_GAP;

    // ==================== 页面 ====================

    /** 只有操作区的主页：内容在标准高度里居中。 */
    public static UIElement page(Widget operation) {
        return RecipeMachinePage.page(operation);
    }

    /** 操作区 + 底部附加区的主页：操作区在剩余空间里居中，底部附加区整宽贴底。 */
    public static UIElement page(Widget operation, UIElement display) {
        display.layout(l -> l.alignSelf(AlignItems.STRETCH));
        return RecipeMachinePage.page(operation, display);
    }

    /** 一行操作控件（一组），组内紧挨。 */
    public static UIElement group(Widget... widgets) {
        return new UIElement().layout(l -> l.row().gapAll(UISizes.GAP).alignCenter()).addChildren(widgets);
    }

    /** 操作区：几组控件横排，组间留出较大的间距。 */
    public static UIElement operations(Widget... groups) {
        return new UIElement().layout(l -> l.row().gapAll(GROUP_GAP).alignCenter()).addChildren(groups);
    }

    // ==================== 槽位网格 ====================

    public static Widget items(KeyInventory<AEItemKey> storage, IO io) {
        return scrollIfTall(itemGrid(storage, io), SlotGrid.squareRows(storage.size()));
    }

    public static Widget tanks(KeyInventory<AEFluidKey> tanks, IO io) {
        return scrollIfTall(SlotGrid.square(tanks.size(), tankSlot(tanks, io)), SlotGrid.squareRows(tanks.size()));
    }

    /** 物品网格右边一列流体槽，两者顶端对齐；任一边超过 {@link #MAX_GRID_ROWS} 行时一起滚动。 */
    public static Widget dual(KeyInventory<AEItemKey> storage, KeyInventory<AEFluidKey> tanks, IO io) {
        var column = UIElement.column(UISizes.SLOT_SIZE);
        var slot = tankSlot(tanks, io);
        for (int i = 0; i < tanks.size(); i++) column.addChild(slot.apply(i));
        var content = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).alignItems(AlignItems.START))
                .addChildren(itemGrid(storage, io), column);
        return scrollIfTall(content, Math.max(SlotGrid.squareRows(storage.size()), tanks.size()));
    }

    private static UIElement itemGrid(KeyInventory<AEItemKey> storage, IO io) {
        var adapter = new MenuItemAdapter(storage);
        return SlotGrid.square(storage.size(), i -> ItemSlot.of(adapter, i, true, io.support(IO.IN)));
    }

    private static IntFunction<Widget> tankSlot(KeyInventory<AEFluidKey> tanks, IO io) {
        var adapter = new ForgeFluidAdapter(tanks);
        return i -> FluidSlot.of(adapter, i, true, io.support(IO.IN));
    }

    /** 超过 {@link #MAX_GRID_ROWS} 行的内容放进滚动区：宽度随内容（加滚动条），高度最多这么多行。 */
    private static Widget scrollIfTall(UIElement content, int rows) {
        if (rows <= MAX_GRID_ROWS) return content;
        int height = MAX_GRID_ROWS * UISizes.SLOT_SIZE;
        var scroller = new ScrollerView("hatch.slots", UISizes.CONTENT_WIDTH, height).adaptiveWidth().setAdaptiveHeight(height);
        scroller.addScrollViewChild(content);
        return scroller;
    }

    // ==================== 单储罐 ====================

    /** 只有一个储罐的仓：操作区是流体槽（输出仓另有锁定开关和锁定流体槽）。 */
    public static UIElement singleTank(NotifiableInventory<AEFluidKey> tank, IO io) {
        return page(tankOperation(tank, io));
    }

    /**
     * 单储罐的操作区：流体槽；输出仓（{@code io} 为 OUT）在右边另起一组锁定开关和锁定流体槽，
     * 锁定后只接受锁定的流体。储罐有流体时不能改锁定流体（与原 GTM 界面一致），锁定槽显示为禁用并说明原因。
     */
    public static UIElement tankOperation(NotifiableInventory<AEFluidKey> tank, IO io) {
        var storage = tank.storage;
        var slot = FluidSlot.of(new ForgeFluidAdapter(storage), 0, true, io.support(IO.IN));
        if (io != IO.OUT) return operations(slot);
        var lockToggle = IconToggle.of(WidgetIcons.ACCESS_PRIVATE, tank::isLocked, tank::setLocked).onOffTooltips(LOCK_ON, LOCK_OFF);
        var lockSlot = PhantomFluidSlot.of(null, 0, () -> lockedFluid(tank), fluid -> {
            // 服务端再判一次：储罐有流体时不能改
            if (!storage.isEmpty()) return;
            if (fluid == null || fluid.isEmpty()) tank.setLocked(false);
            else {
                // 已锁定时 setLocked(true, …) 不会换流体，先解锁
                if (tank.isLocked()) tank.setLocked(false);
                tank.setLocked(true, Keys.fluid(fluid));
            }
        }).disabled(() -> !storage.isEmpty(), LOCK_NEED_EMPTY).xeiPhantom();
        return operations(slot, group(lockToggle, lockSlot));
    }

    private static FluidStack lockedFluid(NotifiableInventory<AEFluidKey> tank) {
        AEKey key = tank.getLockedKey();
        return key instanceof AEFluidKey fluidKey ? fluidKey.getReadOnlyStack() : FluidStack.EMPTY;
    }

    public static Supplier<Component> amountText(LongSupplier amount, LongSupplier capacity, @Nullable String unit) {
        return new LimitText(amount, capacity, unit);
    }

    private static final class LimitText implements Supplier<Component> {

        private final LongSupplier amount;
        private final LongSupplier capacity;
        @Nullable
        private final String unit;
        private long lastAmount = -1, lastCapacity = -1;
        private Component text = Component.empty();

        private LimitText(LongSupplier amount, LongSupplier capacity, @Nullable String unit) {
            this.amount = amount;
            this.capacity = capacity;
            this.unit = unit;
        }

        @Override
        public Component get() {
            long currentAmount = amount.getAsLong(), currentCapacity = capacity.getAsLong();
            if (currentAmount != lastAmount || currentCapacity != lastCapacity) {
                lastAmount = currentAmount;
                lastCapacity = currentCapacity;
                text = unit == null ? Component.translatable("gtceu.gui.hatch.amount_of", FormattingUtil.formatNumbers(currentAmount), FormattingUtil.formatNumbers(currentCapacity)) :
                        Component.translatable("gtceu.gui.hatch.amount_of_unit", FormattingUtil.formatNumbers(currentAmount), FormattingUtil.formatNumbers(currentCapacity), unit);
            }
            return text;
        }
    }
}
