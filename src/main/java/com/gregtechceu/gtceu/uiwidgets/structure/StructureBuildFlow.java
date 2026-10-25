package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.BuildUpload;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBuild;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.CPacketStructureBuild;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.CarriedStock;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderPanel;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class StructureBuildFlow {

    public static final String BUILD_TITLE = "gtceu.structure_build.title";
    public static final String BUILD = "gtceu.structure_build.confirm";
    public static final String INCLUDE = "gtceu.structure_build.include";
    public static final String BLOCKED = "gtceu.structure_build.blocked";
    public static final String DEMOLISH_CONFIRM = "gtceu.structure_build.demolish.confirm";

    private static final Reference2ObjectOpenHashMap<MultiblockMachineDefinition, int[]> REMEMBERED = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<MultiblockMachineDefinition, Choices> CHOICES = new Reference2ObjectOpenHashMap<>();

    private record Choices(int[] values, boolean[] excluded, PatternBuilderModel model) {}

    @FunctionalInterface
    public interface SecondPage {

        UIElement create(Layout layout, int[] values, PatternBuilderScreen.Navigator navigator, StructurePreviewScreen.Context preview);
    }

    private StructureBuildFlow() {}

    public static void clear() {
        CHOICES.clear();
    }

    public static int[] remembered(MultiblockMachineDefinition definition, Structure structure) {
        var values = REMEMBERED.get(definition);
        if (values == null || !structure.accepts(values)) {
            values = structure.defaultValues();
            REMEMBERED.put(definition, values);
        }
        return values;
    }

    public static void remember(MultiblockMachineDefinition definition, int[] values) {
        REMEMBERED.put(definition, values.clone());
    }

    public static StructurePreviewScreen.Action action(String labelKey, SecondPage second) {
        return new StructurePreviewScreen.Action(labelKey, preview -> PatternBuilderScreen.open(
                navigator -> second.create(preview.layout(), preview.values(), navigator, preview)));
    }

    public static void onTerminalUse(IMultiController controller) {
        if (StructureProjection.isProjecting(controller.self().getPos())) StructureProjection.clear();
        else openTerminal(controller);
    }

    public static boolean cancelProjection() {
        if (!StructureProjection.isActive()) return false;
        StructureProjection.clear();
        return true;
    }

    public static void openTerminal(IMultiController controller) {
        var machine = controller.self();
        var definition = machine.getDefinition();
        var structure = definition.displayStructure();
        if (structure == null) return;
        var pos = machine.getPos();
        var icon = definition.asStack();
        var modes = new TerminalModes(machine.allowFlip(), structure.tree());
        var project = new StructurePreviewScreen.Action(StructurePreviewScreen.PROJECT, preview -> {
            var layout = preview.layout();
            var chosen = chosen(definition, preview.values(), layout.excludedNodes());
            var items = chosen != null ? StructurePlans.assign(chosen, layout) : StructurePlans.preview(definition, layout).items();
            StructureProjection.project(controller, layout, items, modes.isMirror());
            preview.closePreview();
        });
        StructurePreviewScreen.open(definition, structure, null, modes, project, action(StructurePreviewScreen.BUILD, (layout, values, navigator, preview) -> {
            var stock = layoutStock(Minecraft.getInstance().player, layout);
            var excluded = layout.excludedNodes();
            var model = chosen(definition, values, excluded);
            if (model == null) {
                model = StructurePlans.modelBuilder(icon, layout, false).build(stock);
                model.selectMinimum();
                CHOICES.put(definition, new Choices(values.clone(), excluded, model));
            }
            var built = model;
            boolean demolish = modes.isDemolish();
            return new PatternBuilderPanel(model, icon, icon.getHoverName(), Integer.MAX_VALUE, navigator.maxHeight(), () -> {
                int upload = BuildUpload.send(definition, values, StructurePlans.assign(built, layout));
                int flags = (modes.isMirror() ? StructureBuild.FLAG_MIRROR : 0) | (TerminalModes.isNetwork() ? StructureBuild.FLAG_NETWORK : 0) |
                        (demolish ? StructureBuild.FLAG_DEMOLISH : 0);
                if (upload >= 0) GTNetwork.NETWORK.sendToServer(new CPacketStructureBuild(pos, values, upload, flags, modes.getDemolishNodes()));
                preview.closePreview();
            }, navigator::close, new PatternBuilderPanel.Footer(BUILD_TITLE, demolish ? DEMOLISH_CONFIRM : BUILD, INCLUDE, BLOCKED, false, !demolish,
                    navigator::close));
        }));
    }

    private static ReferenceOpenHashSet<Item> wantedItems(Layout layout) {
        var items = new ReferenceOpenHashSet<Item>();
        for (var predicate : new ReferenceOpenHashSet<>(layout.predicates())) {
            if (predicate != null) items.addAll(StructureBuild.candidates(predicate));
        }
        return items;
    }

    @Nullable
    public static PatternBuilderModel chosen(MultiblockMachineDefinition definition, int[] values, boolean[] excludedNodes) {
        var choices = CHOICES.get(definition);
        return choices != null && Arrays.equals(choices.values(), values) && sameExclusion(choices.excluded(), excludedNodes) ? choices.model() : null;
    }

    private static boolean sameExclusion(boolean[] a, boolean[] b) {
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            if ((i < a.length && a[i]) != (i < b.length && b[i])) return false;
        }
        return true;
    }

    public static Function<ItemStack, PatternBuilderModel.Stock> inventoryStock(Player player) {
        return inventoryStock(player, false, List.of());
    }

    public static Function<ItemStack, PatternBuilderModel.Stock> layoutStock(Player player, Layout layout) {
        return inventoryStock(player, TerminalModes.isNetwork(), wantedItems(layout));
    }

    public static Function<ItemStack, PatternBuilderModel.Stock> inventoryStock(Player player, boolean network, Collection<Item> wanted) {
        if (player == null || player.isCreative() || !CarriedStock.request(network, wanted)) return stack -> null;
        return stack -> new PatternBuilderModel.Stock(-1, false, CarriedStock.get(stack.getItem()));
    }
}
