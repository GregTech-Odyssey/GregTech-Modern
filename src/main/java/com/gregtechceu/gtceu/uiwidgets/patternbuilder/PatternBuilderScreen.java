package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.window.Popup;
import com.gregtechceu.gtceu.uipro.window.PopupCard;
import com.gregtechceu.gtceu.uipro.window.WindowConfiguratorPanel;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class PatternBuilderScreen extends ModularUIGuiContainer {

    public static final String SORT = "gtceu.pattern_builder.sort";
    public static final String SORT_STOCK = "gtceu.pattern_builder.sort.stock";
    public static final String SORT_CRAFTABLE = "gtceu.pattern_builder.sort.craftable";
    public static final String SORT_NONE = "gtceu.pattern_builder.sort.none";
    private static final PatternBuilderModel.Sort[] SORTS = PatternBuilderModel.Sort.values();

    private final Host host;
    private boolean closed;

    private PatternBuilderScreen(Host host, ModularUI ui) {
        super(ui, -1);
        this.host = host;
        ui.initWidgets();
    }

    public static void open(PatternBuilderModel model, ItemStack icon, Component title, int inputLimit, Runnable onWrite) {
        var minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            if (!(minecraft.screen instanceof AbstractContainerScreen<?>) || minecraft.player == null) return;
            var window = minecraft.getWindow();
            int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
            int maxHeight = (int) (height * UISizes.MAX_WINDOW_SCREEN_RATIO);
            var ref = new PatternBuilderScreen[1];
            Runnable close = () -> {
                if (ref[0] != null) ref[0].onClose();
            };
            var panel = new PatternBuilderPanel(model, icon, title, inputLimit, maxHeight, () -> {
                onWrite.run();
                close.run();
            }, close);
            var host = new Host(width, height, panel);
            var ui = new ModularUI(width, height, IUIHolder.EMPTY, minecraft.player).widget(host);
            ref[0] = new PatternBuilderScreen(host, ui);
            ForgeHooksClient.pushGuiLayer(minecraft, ref[0]);
        });
    }

    @Override
    public void init() {
        modularUI.setSize(width, height);
        host.setSize(new Size(width, height));
        super.init();
        host.place();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!host.panel.isMouseOverElement(mouseX, mouseY) && !host.configurators.isMouseOverElement(mouseX, mouseY) &&
                (host.popup == null || !host.popup.isMouseOverElement(mouseX, mouseY))) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (closed) return;
        closed = true;
        if (minecraft != null && minecraft.screen == this) ForgeHooksClient.popGuiLayer(minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Host extends WidgetGroup implements ILayoutHost, ILocalUI, PatternBuilderPanel.PopupSlot {

        private static final String POPUP_ID = "pattern_builder.popup";

        private final PatternBuilderPanel panel;
        private final WindowConfiguratorPanel configurators = new WindowConfiguratorPanel();
        @Nullable
        private PopupCard popup;
        @Nullable
        private String popupKey;
        @Nullable
        private Supplier<Popup> popupFactory;

        private Host(int width, int height, PatternBuilderPanel panel) {
            super(0, 0, width, height);
            this.panel = panel;
            setClientSideWidget();
            addWidget(panel);
            configurators.setTexture(UITheme.CONFIGURATOR_TAB);
            configurators.attachConfigurators(new SortConfigurator(panel));
            addWidget(configurators);
            panel.setPopupSlot(this);
        }

        private int popupMaxHeight() {
            return Math.max(UISizes.SLOT, getSizeHeight() - 2 * UISizes.POPUP_SCREEN_MARGIN);
        }

        private void place() {
            int width = getSizeWidth(), height = getSizeHeight(), margin = UISizes.POPUP_SCREEN_MARGIN;
            int extra = popup == null ? 0 : UISizes.POPUP_GAP + popup.getSizeWidth();
            int x = (width - panel.getSizeWidth()) / 2;
            if (x + panel.getSizeWidth() + extra > width - margin) {
                x = Math.max(margin + configurators.getSizeWidth() + UISizes.GAP, width - margin - panel.getSizeWidth() - extra);
            }
            int y = (height - panel.getSizeHeight()) / 2;
            panel.setSelfPosition(new Position(x, y));
            configurators.setAvailableHeight(panel.getSizeHeight() - UISizes.WINDOW_PADDING_TOP - UISizes.WINDOW_PADDING_BOTTOM);
            configurators.attachConfigurators();
            configurators.setSelfPosition(new Position(x - configurators.getSizeWidth() - UISizes.GAP, y + UISizes.WINDOW_PADDING_TOP));
            if (popup != null) {
                popup.setMaxHeight(popupMaxHeight());
                int popupY = Math.max(margin, Math.min(y, height - margin - popup.getSizeHeight()));
                popup.setSelfPosition(new Position(x + panel.getSizeWidth() + UISizes.POPUP_GAP, popupY));
            }
        }

        @Override
        public void toggle(String key, Supplier<Popup> factory) {
            if (key.equals(popupKey)) closePopup();
            else openPopup(key, factory);
        }

        @Override
        public boolean isOpen(String key) {
            return key.equals(popupKey);
        }

        @Override
        public void rebuild() {
            if (popupKey != null && popupFactory != null) openPopup(popupKey, popupFactory);
        }

        private void openPopup(String key, Supplier<Popup> factory) {
            if (popup != null) removeWidget(popup);
            popupKey = key;
            popupFactory = factory;
            popup = new PopupCard(POPUP_ID, factory.get(), popupMaxHeight(), this::closePopup);
            addWidget(1, popup);
            place();
        }

        private void closePopup() {
            if (popup != null) removeWidget(popup);
            popup = null;
            popupKey = null;
            popupFactory = null;
            place();
        }

        @Override
        public void onContentResized(Widget root) {
            if (root == panel || root == popup) place();
        }

        @Override
        protected void onChildSizeUpdate(@Nullable Widget child) {}
    }

    private record SortConfigurator(PatternBuilderPanel panel) implements IFancyConfigurator {

        private static final int WIDTH = 2 * UISizes.BUTTON_WIDTH + 2 * UISizes.VALUE_WIDTH;

        @Override
        public Component getTitle() {
            return Component.translatable(SORT);
        }

        @Override
        public IGuiTexture getIcon() {
            return WidgetIcons.FILTER;
        }

        @Override
        public Widget createConfigurator() {
            return ButtonGroup.single(SORTS.length, index -> Component.translatable(switch (SORTS[index]) {
                case STOCK -> SORT_STOCK;
                case CRAFTABLE -> SORT_CRAFTABLE;
                case NONE -> SORT_NONE;
            }), () -> panel.getSort().ordinal(), index -> panel.setSort(SORTS[index])).layout(l -> l.width(WIDTH));
        }
    }
}
