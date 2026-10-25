package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureTree;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;

/**
 * 普通终端结构预览的镜像、拆除与取料模式：预览、结构树、投影与建造共用同一份状态，变化时递增版本号。
 */
@OnlyIn(Dist.CLIENT)
public final class TerminalModes {

    public static final String MIRROR = "gtceu.structure_build.mirror";
    public static final String MIRROR_DESC = "gtceu.structure_build.mirror.desc";
    public static final String MIRROR_UNSUPPORTED = "gtceu.structure_build.mirror.unsupported";
    public static final String DEMOLISH = "gtceu.structure_build.demolish";
    public static final String DEMOLISH_DESC = "gtceu.structure_build.demolish.desc";
    public static final String DEMOLISH_NODE = "gtceu.structure_build.demolish.node";
    public static final String DEMOLISH_NODE_DESC = "gtceu.structure_build.demolish.node.desc";
    public static final String NETWORK = "gtceu.structure_build.network";
    public static final String NETWORK_DESC = "gtceu.structure_build.network.desc";
    private static final String MIRROR_PREFERENCE = "structure_build.mirror";
    private static final String NETWORK_PREFERENCE = "structure_build.network";

    private final boolean canMirror;
    private final boolean[] demolishNodes;
    private boolean demolish;
    private int version;

    public TerminalModes(boolean canMirror, StructureTree tree) {
        this.canMirror = canMirror;
        this.demolishNodes = new boolean[tree.nodes().length];
        Arrays.fill(demolishNodes, true);
    }

    public int getVersion() {
        return version;
    }

    public boolean isMirror() {
        return canMirror && Boolean.parseBoolean(UIPreferences.get(MIRROR_PREFERENCE, "false"));
    }

    public void setMirror(boolean mirror) {
        if (mirror == isMirror()) return;
        UIPreferences.put(MIRROR_PREFERENCE, String.valueOf(mirror));
        version++;
    }

    public boolean isDemolish() {
        return demolish;
    }

    public void setDemolish(boolean demolish) {
        if (this.demolish == demolish) return;
        this.demolish = demolish;
        version++;
    }

    public static boolean isNetwork() {
        return Boolean.parseBoolean(UIPreferences.get(NETWORK_PREFERENCE, "false"));
    }

    public static void setNetwork(boolean network) {
        UIPreferences.put(NETWORK_PREFERENCE, String.valueOf(network));
    }

    public static UIElement networkRow() {
        return Form.controlRow(NETWORK, Switch.of(TerminalModes::isNetwork, TerminalModes::setNetwork), NETWORK_DESC);
    }

    public boolean isDemolishing(int node) {
        return node < 0 || node >= demolishNodes.length || demolishNodes[node];
    }

    public void setDemolishing(int node, boolean on) {
        if (node < 0 || node >= demolishNodes.length || demolishNodes[node] == on) return;
        demolishNodes[node] = on;
        version++;
    }

    public boolean[] getDemolishNodes() {
        return demolishNodes.clone();
    }

    public UIElement options() {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(
                Form.controlRow(MIRROR, Switch.of(this::isMirror, this::setMirror).disabled(() -> !canMirror, MIRROR_UNSUPPORTED), MIRROR_DESC),
                Form.controlRow(DEMOLISH, Switch.of(this::isDemolish, this::setDemolish), DEMOLISH_DESC),
                networkRow());
    }
}
