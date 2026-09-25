package com.gregtechceu.gtceu.uiwidgets.inventory;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeMachinePage;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 输入/输出总线、输入/输出仓、输入/输出总成这类"被动"部件的主页，统一成上下两区：
 *
 * <pre>
 *  ┌──────────────────────────────────┐
 *  │        [槽] │ [锁] [锁定槽]        │  操作区：槽位、开关，在剩余空间里居中
 *  │                                  │
 *  │ ┌ 流体 ………………………………… 水 ┐ │
 *  │ │ 储量 …… 1,000 / 8,000 mB │ │  显示区：状态面板，整宽贴在页面底边（紧挨玩家背包）
 *  │ └ 锁定流体 ……………………… 无 ┘ │
 *  └──────────────────────────────────┘
 * </pre>
 *
 * 页面与单方块配方机器同一标准高度（内容更高才撑高）。只有槽位网格、没有数据要显示的部件（总线、多格仓、总成）只有操作区；
 * 网格超过 {@link #MAX_GRID_ROWS} 行时放进滚动区。蒸汽部件保留自己的铜/钢皮肤，用 {@link #fixedPage} 按皮肤留出的区域摆放同样的两区。
 * 输出方向的槽只能取出、不能放入（与原 GTM 界面一致）。
 */
public final class HatchViews {

    private HatchViews() {}

    /** "存储数量"（大容量单格总线等）。 */
    public static final String STORED = "gtceu.gui.hatch.stored";
    /** "物品"（大容量单格总线里存的物品）。 */
    public static final String ITEM = "gtceu.gui.hatch.item";
    /** "取出一组"按钮说明。 */
    public static final String EXTRACT_STACK = "gtceu.gui.hatch.extract_stack";
    /** 显示区里物品 / 流体为空时的"空"。 */
    public static final String EMPTY = "gtceu.gui.hatch.empty";
    private static final String FLUID = "gtceu.gui.hatch.fluid";
    private static final String AMOUNT = "gtceu.gui.hatch.amount";
    private static final String LOCKED = "gtceu.gui.hatch.locked";
    private static final String NOT_LOCKED = "gtceu.gui.hatch.not_locked";
    private static final String LOCK_ON = "gtceu.gui.hatch.lock_on";
    private static final String LOCK_OFF = "gtceu.gui.hatch.lock_off";
    private static final String LOCK_NEED_EMPTY = "gtceu.gui.hatch.lock_need_empty";

    /** 槽位网格最多直接显示的行数，超出放进滚动区（与多方块状态显示窗同高）。 */
    public static final int MAX_GRID_ROWS = UISizes.MACHINE_PAGE_HEIGHT / UISizes.SLOT;
    /** 操作区里不同组控件之间的间距（如储罐槽与锁定开关之间）。 */
    public static final int GROUP_GAP = 2 * UISizes.SECTION_GAP;

    // ==================== 页面 ====================

    /** 只有操作区的主页：内容在标准高度里居中。 */
    public static UIElement page(Widget operation) {
        return RecipeMachinePage.page(operation);
    }

    /** 操作区 + 显示区的主页：操作区在剩余空间里居中，显示区整宽贴底。 */
    public static UIElement page(Widget operation, UIElement display) {
        display.layout(l -> l.alignSelf(AlignItems.STRETCH));
        return RecipeMachinePage.page(operation, display);
    }

    /**
     * 固定区域（{@code width} × {@code height}）里的两区，给保留原皮肤、按坐标摆放的界面（蒸汽部件）用：
     * 操作区在上方剩余空间里居中，显示区贴底、宽 {@code displayWidth} 靠左（右边让出皮肤上的图案）。
     */
    public static UIElement fixedPage(int width, int height, Widget operation, UIElement display, int displayWidth) {
        var center = new UIElement().layout(l -> l.column().flexGrow(1).alignCenter().justifyContent(AlignContent.CENTER));
        center.addChild(operation);
        display.layout(l -> l.width(displayWidth).alignSelf(AlignItems.START));
        return new UIElement().layout(l -> l.column().size(width, height).gapAll(UISizes.SECTION_GAP))
                .addChildren(center, display);
    }

    /** 固定区域里只有操作区（居中），给保留原皮肤、按坐标摆放的界面用。 */
    public static UIElement fixedPage(int width, int height, Widget operation) {
        return new UIElement().layout(l -> l.column().size(width, height).alignCenter().justifyContent(AlignContent.CENTER))
                .addChild(operation);
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

    public static Widget items(CustomItemStackHandler storage, IO io) {
        return scrollIfTall(itemGrid(storage, io, null), SlotGridView.rows(storage.getSlots()));
    }

    /** 换了槽底图的物品网格（蒸汽部件用铜/钢色槽，与蒸汽界面皮肤一致）。 */
    public static Widget items(CustomItemStackHandler storage, IO io, IGuiTexture slotTexture) {
        return scrollIfTall(itemGrid(storage, io, slotTexture), SlotGridView.rows(storage.getSlots()));
    }

    public static Widget tanks(CustomFluidTank[] tanks, IO io) {
        return scrollIfTall(SlotGridView.grid(tanks.length, tankSlot(tanks, io)), SlotGridView.rows(tanks.length));
    }

    /** 物品网格右边一列流体槽，两者顶端对齐；任一边超过 {@link #MAX_GRID_ROWS} 行时一起滚动。 */
    public static Widget dual(CustomItemStackHandler storage, CustomFluidTank[] tanks, IO io) {
        var column = UIElement.column(UISizes.SLOT);
        var slot = tankSlot(tanks, io);
        for (int i = 0; i < tanks.length; i++) column.addChild(slot.apply(i));
        var content = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).alignItems(AlignItems.START))
                .addChildren(itemGrid(storage, io, null), column);
        return scrollIfTall(content, Math.max(SlotGridView.rows(storage.getSlots()), tanks.length));
    }

    private static UIElement itemGrid(CustomItemStackHandler storage, IO io, @Nullable IGuiTexture slotTexture) {
        return SlotGridView.grid(storage.getSlots(), i -> {
            var slot = new ItemSlot(storage, i, true, io.support(IO.IN));
            if (slotTexture != null) slot.setBackgroundTexture(slotTexture);
            return slot;
        });
    }

    private static IntFunction<Widget> tankSlot(CustomFluidTank[] tanks, IO io) {
        return i -> new FluidSlot(tanks[i], 0, true, io.support(IO.IN));
    }

    /** 超过 {@link #MAX_GRID_ROWS} 行的内容放进滚动区：宽度随内容（加滚动条），高度最多这么多行。 */
    private static Widget scrollIfTall(UIElement content, int rows) {
        if (rows <= MAX_GRID_ROWS) return content;
        int height = MAX_GRID_ROWS * UISizes.SLOT;
        var scroller = new ScrollerView("hatch.slots", UISizes.CONTENT_WIDTH, height).adaptiveWidth().adaptiveHeight(height);
        scroller.addScrollViewChild(content);
        return scroller;
    }

    // ==================== 单储罐 ====================

    /** 只有一个储罐的仓：操作区是流体槽（输出仓另有锁定开关和锁定流体槽），显示区是流体、储量（输出仓另有锁定流体）。 */
    public static UIElement singleTank(NotifiableFluidTank tank, IO io) {
        return page(tankOperation(tank, io), tankStatus(tank, io));
    }

    /**
     * 单储罐的操作区：流体槽；输出仓（{@code io} 为 OUT）在右边另起一组锁定开关和锁定流体槽，
     * 锁定后只接受锁定的流体。储罐有流体时不能改锁定流体（与原 GTM 界面一致），锁定槽显示为禁用并说明原因。
     */
    public static UIElement tankOperation(NotifiableFluidTank tank, IO io) {
        var storage = tank.getStorages()[0];
        var slot = new FluidSlot(storage, 0, true, io.support(IO.IN));
        if (io != IO.OUT) return operations(slot);
        var locked = tank.getLockedFluid();
        var lockToggle = IconToggle.of(WidgetIcons.ACCESS_PRIVATE, tank::isLocked, tank::setLocked).tooltips(LOCK_ON, LOCK_OFF);
        var lockSlot = new PhantomFluidSlot(locked, 0, locked::getFluid, fluid -> {
            // 服务端再判一次：储罐有流体时不能改
            if (!storage.getFluid().isEmpty()) return;
            if (fluid == null || fluid.isEmpty()) tank.setLocked(false);
            else {
                // 已锁定时 setLocked(true, …) 不会换流体，先解锁
                if (tank.isLocked()) tank.setLocked(false);
                tank.setLocked(true, fluid);
            }
        }).disabled(() -> !storage.getFluid().isEmpty(), LOCK_NEED_EMPTY).xeiPhantom();
        return operations(slot, group(lockToggle, lockSlot));
    }

    /** 单储罐的显示区：流体、储量；输出仓另有锁定流体。 */
    public static StatusPanel tankStatus(NotifiableFluidTank tank, IO io) {
        var storage = tank.getStorages()[0];
        var status = new StatusPanel();
        status.addLine(FLUID, new FluidName(storage::getFluid, EMPTY));
        status.addLine(AMOUNT, new AmountText(storage));
        if (io == IO.OUT) {
            var locked = tank.getLockedFluid();
            status.addLine(LOCKED, new FluidName(() -> tank.isLocked() ? locked.getFluid() : FluidStack.EMPTY, NOT_LOCKED));
        }
        return status;
    }

    /** 流体名称，没有流体时显示 {@code emptyKey}。流体不变时复用上次的文字。 */
    private static final class FluidName implements Supplier<Component> {

        private final Supplier<FluidStack> fluid;
        private final String emptyKey;
        private FluidStack last = FluidStack.EMPTY;
        private Component text;

        private FluidName(Supplier<FluidStack> fluid, String emptyKey) {
            this.fluid = fluid;
            this.emptyKey = emptyKey;
            this.text = Component.translatable(emptyKey);
        }

        @Override
        public Component get() {
            var current = fluid.get();
            if (current.isEmpty() != last.isEmpty() || !current.isFluidEqual(last)) {
                last = current.copy();
                text = current.isEmpty() ? Component.translatable(emptyKey) : current.getDisplayName();
            }
            return text;
        }
    }

    /** 储量"数量 / 容量 mB"。数量和容量不变时复用上次的文字。 */
    private static final class AmountText implements Supplier<Component> {

        private final CustomFluidTank storage;
        private long amount = -1, capacity = -1;
        private Component text = Component.empty();

        private AmountText(CustomFluidTank storage) {
            this.storage = storage;
        }

        @Override
        public Component get() {
            long currentAmount = storage.getFluidAmount(), currentCapacity = storage.getCapacity();
            if (currentAmount != amount || currentCapacity != capacity) {
                amount = currentAmount;
                capacity = currentCapacity;
                text = Component.literal(FormattingUtil.formatNumbers(amount) + " / " + FormattingUtil.formatNumbers(capacity) + " mB");
            }
            return text;
        }
    }
}
