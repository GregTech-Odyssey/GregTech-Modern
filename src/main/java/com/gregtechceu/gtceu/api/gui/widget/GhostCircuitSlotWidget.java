package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uipro.window.Popup;
import com.gregtechceu.gtceu.uipro.window.PopupHost;
import com.gregtechceu.gtceu.uiwidgets.circuit.CircuitSelector;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Used for setting a "ghost" IC for a machine
 * <p>
 * 槽本身的操作不变：左键 +1、右键 -1、滚轮加减、Shift+右键清空、Shift+左键开关电路选择面板。
 * 面板内容是新式 {@link CircuitSelector}（当前电路、清除、编号网格），按钮都在服务端执行，所以两端都要有这份面板，开关以服务端为准：
 * <ul>
 * <li><b>在 {@link MachineWindow} 里</b>：用框架的弹出面板（键 {@link #POPUP_KEY}），两端在 {@link #initWidget()} 里注册。
 * 外框、标题行与 {@code [×]}、限高与滚动、开关同步都由框架负责；面板在窗口右侧、不盖住界面，打开期间槽显示统一选中框。
 * 一个页面只应有一个电路槽（键相同，后注册的覆盖前一个）。</li>
 * <li><b>不在 MachineWindow 里（退路）</b>：没有面板容器，面板（与弹出面板同一外观，{@link PopupHost#standalonePanel}）
 * 挂在 {@code gui.mainGroup} 末尾。打开期间客户端把界面其余部分隐藏并停用（模态），下面的控件不会再响应悬停、提示、EMI 取物、
 * 滚轮和点击；服务端照常同步。客户端只发"请求打开 / 关闭"，服务端校验后先通知客户端照做、再改自己那份，新面板的初始数据经
 * {@code addWidget} 的初始化通道下发（按主控件组下标寻址，所以主控件组里不能有只在客户端存在的控件）。
 * 槽被移出控件树时两端各自关掉面板（两端的移除本来就对称，不发包）。</li>
 * </ul>
 */
public class GhostCircuitSlotWidget extends SlotWidget {

    private static final int SET_TO_ZERO = 1;
    private static final int SET_TO_EMPTY = 2;
    private static final int SET_TO_N = 3;
    /// 退路面板：客户端请求打开 / 关闭（C2S）；服务端照做前用同一 ID 通知客户端（S2C）。在 MachineWindow 里不用
    private static final int OPEN_FALLBACK = 4;
    private static final int CLOSE_FALLBACK = 5;
    private static final int NO_CONFIG = -1;
    /// 弹出面板的键；这种面板只有一个，参数恒为 0
    private static final String POPUP_KEY = "gtceu.circuit_configurator";
    private static final String CONFIGURATOR_TITLE = "gtceu.gui.programmed_circuit_configuration";

    @Getter
    private ICustomItemStackHandler circuitInventory;
    /// 退路面板（不在 MachineWindow 里时），null 表示没打开
    @Nullable
    private Widget fallbackPanel;
    /// 客户端：退路面板打开时被隐藏 / 停用的其他控件，关闭时按原样恢复
    private final List<Widget> fallbackHidden = new ArrayList<>();
    private final List<Widget> fallbackDeactivated = new ArrayList<>();
    /// 服务端：上次处理退路面板开关的游戏刻，同一刻内只处理一次（防刷）
    private long lastFallbackToggleTick = Long.MIN_VALUE;

    public GhostCircuitSlotWidget() {
        super();
    }

    public void setCircuitInventory(ICustomItemStackHandler circuitInventory) {
        this.circuitInventory = circuitInventory;
        setHandlerSlot(circuitInventory, 0);
    }

    /** 两端都会执行：在所在的机器窗口里注册电路选择面板（切换页面时框架会清空注册表，新页面的槽重新注册）。 */
    @Override
    public void initWidget() {
        super.initWidget();
        var window = MachineWindow.of(this);
        if (window != null) {
            window.registerPopup(POPUP_KEY, argument -> argument == 0 && circuitInventory != null ? createPopup() : null);
        }
    }

    public boolean isConfiguratorOpen() {
        var window = MachineWindow.of(this);
        return window != null ? window.isPopupOpen(POPUP_KEY) : fallbackPanel != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverElement(mouseX, mouseY) && gui != null) {
            if (button == 0 && Screen.hasShiftDown()) {
                // open popup on shift-left-click
                toggleConfigurator();
            } else if (button == 0) {
                // increment on left-click
                int newValue = getNextValue(true);
                setCircuitValue(newValue);
            } else if (button == 1 && Screen.hasShiftDown()) {
                // clear on shift-right-click
                this.circuitInventory.setStackInSlot(0, ItemStack.EMPTY);
                writeClientAction(SET_TO_EMPTY, buf -> {});
            } else if (button == 1) {
                // decrement on right-click
                int newValue = getNextValue(false);
                setCircuitValue(newValue);
            }
            return true;
        }
        return false;
    }

    private int getNextValue(boolean increment) {
        int currentValue = IntCircuitBehaviour.getCircuitConfiguration(this.circuitInventory.getStackInSlot(0));
        if (increment) {
            // if at max, loop around to no circuit
            if (currentValue == IntCircuitBehaviour.CIRCUIT_MAX) {
                return 0;
            }
            // if at no circuit, skip 0 and return 1
            if (this.circuitInventory.getStackInSlot(0).isEmpty()) {
                return 1;
            }
            // normal case: increment by 1
            return currentValue + 1;
        } else {
            // if at no circuit, loop around to max
            if (this.circuitInventory.getStackInSlot(0).isEmpty()) {
                return IntCircuitBehaviour.CIRCUIT_MAX;
            }
            // if at 1, skip 0 and return no circuit
            if (currentValue == 1) {
                return NO_CONFIG;
            }
            // normal case: decrement by 1
            return currentValue - 1;
        }
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (isMouseOverElement(mouseX, mouseY) && gui != null) {
            int newValue = getNextValue(wheelDelta >= 0);
            setCircuitValue(newValue);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        return false;
    }

    public void setCircuitValue(int newValue) {
        if (newValue == NO_CONFIG) {
            this.circuitInventory.setStackInSlot(0, ItemStack.EMPTY);
            writeClientAction(SET_TO_EMPTY, buf -> {});
        } else {
            this.circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(newValue));
            writeClientAction(SET_TO_N, buf -> buf.writeVarInt(newValue));
        }
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (circuitInventory == null) return;
        switch (id) {
            case SET_TO_ZERO -> this.circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(0));
            case SET_TO_EMPTY -> this.circuitInventory.setStackInSlot(0, ItemStack.EMPTY);
            case SET_TO_N -> {
                // 编号来自客户端，可被伪造：只接受 0..CIRCUIT_MAX，越界直接忽略
                int value = buffer.readVarInt();
                if (value >= 0 && value <= IntCircuitBehaviour.CIRCUIT_MAX) {
                    this.circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(value));
                }
            }
            case OPEN_FALLBACK -> serverSetFallbackOpen(true);
            case CLOSE_FALLBACK -> serverSetFallbackOpen(false);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        switch (id) {
            case OPEN_FALLBACK -> setFallbackOpen(true);
            case CLOSE_FALLBACK -> setFallbackOpen(false);
            default -> super.readUpdateInfo(id, buffer);
        }
    }

    /** 面板打开期间，槽画统一选中框（前景层，见 {@link UITheme#drawSelection}）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var window = MachineWindow.of(this);
        if (window != null && window.isPopupOpen(POPUP_KEY)) {
            UITheme.drawSelection(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    /** 客户端：开关电路选择面板，由服务端决定。 */
    private void toggleConfigurator() {
        var window = MachineWindow.of(this);
        if (window != null) window.togglePopup(POPUP_KEY, 0);
        else requestFallback(fallbackPanel == null);
    }

    /** 面板内容：一个区块，里面是电路选择器；标题由面板外框显示。 */
    private Popup createPopup() {
        return Popup.of(() -> Component.translatable(CONFIGURATOR_TITLE),
                column -> column.addChild(UIElement.section().addChild(CircuitSelector.create(circuitInventory))));
    }

    // ==================== 退路：不在 MachineWindow 里 ====================

    /** 客户端：请求打开 / 关闭退路面板。 */
    private void requestFallback(boolean open) {
        writeClientAction(open ? OPEN_FALLBACK : CLOSE_FALLBACK, buf -> {});
    }

    /** 服务端：状态确实要变、且本刻还没处理过时，先通知客户端照做，再改自己那份。 */
    private void serverSetFallbackOpen(boolean open) {
        if (gui == null || MachineWindow.of(this) != null || open == (fallbackPanel != null)) return;
        long tick = gui.entityPlayer.level().getGameTime();
        if (tick == lastFallbackToggleTick) return;
        lastFallbackToggleTick = tick;
        writeUpdateInfo(open ? OPEN_FALLBACK : CLOSE_FALLBACK, buf -> {});
        setFallbackOpen(open);
    }

    /** 本端增删退路面板：加在主控件组末尾（两端下标一致、最后绘制）；客户端同时隐藏 / 恢复界面其余部分。 */
    private void setFallbackOpen(boolean open) {
        if (gui == null || open == (fallbackPanel != null)) return;
        var root = gui.mainGroup;
        if (open) {
            fallbackPanel = PopupHost.standalonePanel(POPUP_KEY, createPopup(), () -> requestFallback(false));
            root.addWidget(fallbackPanel);
            // 在界面中央；比界面大时贴左上，不伸出界面左上方
            fallbackPanel.setSelfPosition(new Position(Math.max(0, (root.getSizeWidth() - fallbackPanel.getSizeWidth()) / 2),
                    Math.max(0, (root.getSizeHeight() - fallbackPanel.getSizeHeight()) / 2)));
            if (isRemote()) hideOthers(root.widgets, fallbackPanel);
        } else {
            var panel = fallbackPanel;
            fallbackPanel = null;
            restoreOthers();
            root.removeWidget(panel);
        }
    }

    /**
     * 客户端：模态——隐藏并停用主控件组里除面板外的所有控件，只记下原本可见 / 可用的，关闭时只恢复这些。
     * 只在客户端做：服务端的控件保持可用，照常同步。
     */
    private void hideOthers(List<Widget> widgets, Widget panel) {
        for (var widget : widgets) {
            if (widget == panel) continue;
            if (widget.isVisible()) {
                widget.setVisible(false);
                fallbackHidden.add(widget);
            }
            if (widget.isActive()) {
                widget.setActive(false);
                fallbackDeactivated.add(widget);
            }
        }
    }

    /** 恢复被隐藏 / 停用的控件（服务端两个列表都是空的）。 */
    private void restoreOthers() {
        for (var widget : fallbackHidden) widget.setVisible(true);
        for (var widget : fallbackDeactivated) widget.setActive(true);
        fallbackHidden.clear();
        fallbackDeactivated.clear();
    }

    /**
     * 离开界面（被移出控件树，或换到别的界面）时收起退路面板。两端对控件树的增删是对称的，所以各自在本端收起、不发包；
     * 此时可能正处在主控件组的遍历中（如服务端 {@code detectAndSendChanges}），面板延到本轮结束再摘。
     * 在 MachineWindow 里时，面板状态归窗口：服务端关掉它并通知客户端（切换页面时窗口本来也会清空，这里只是多一条无害的关闭）。
     */
    @Override
    public void setGui(ModularUI gui) {
        var old = this.gui;
        if (old != null && gui != old) {
            if (fallbackPanel != null) {
                var panel = fallbackPanel;
                fallbackPanel = null;
                restoreOthers();
                old.mainGroup.waitToRemoved(panel);
            } else if (!isRemote()) {
                var window = MachineWindow.of(this);
                if (window != null && window.isPopupOpen(POPUP_KEY)) window.closePopup(POPUP_KEY);
            }
        }
        super.setGui(gui);
    }
}
