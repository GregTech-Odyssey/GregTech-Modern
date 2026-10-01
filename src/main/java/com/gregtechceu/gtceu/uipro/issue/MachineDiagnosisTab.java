package com.gregtechceu.gtceu.uipro.issue;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.IFirstVisitTab;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public final class MachineDiagnosisTab implements IFancyUIProvider, IFirstVisitTab {

    private static final String TITLE = "gtceu.gui.machine_diagnosis";

    private static final String SCROLLER_ID = "machine.diagnosis";
    private static final String FIRST_VISIT_KEY = "diagnosis";

    private final IRecipeLogicMachine machine;

    public MachineDiagnosisTab(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    public static void attach(TabsWidget sideTabs, IRecipeLogicMachine machine) {
        sideTabs.attachSubTab(0, new MachineDiagnosisTab(machine));
    }

    public static void attachIfEnabled(TabsWidget sideTabs, Object machine) {
        if (machine instanceof IRecipeLogicMachine logicMachine && logicMachine.hasDiagnosisTab()) attach(sideTabs, logicMachine);
    }

    public IRecipeLogicMachine getMachine() {
        return machine;
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        var scroller = ScrollerView.page(SCROLLER_ID, UISizes.CONTENT_WIDTH).addScrollViewChild(DiagnosisView.of(LayoutStyle.AUTO, machine));
        return UIElement.column(UISizes.CONTENT_WIDTH).addChild(scroller);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return WidgetIcons.DIAGNOSIS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE);
    }

    @Override
    public List<Component> getTabTooltips() {
        return Collections.singletonList(Component.translatable(TITLE));
    }

    @Override
    public MetaMachine getIssueMachine() {
        return machine.self();
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        tooltipsPanel.attachRecipeLogics(machine.self());
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }

    @Override
    public String getFirstVisitKey() {
        return FIRST_VISIT_KEY;
    }
}
