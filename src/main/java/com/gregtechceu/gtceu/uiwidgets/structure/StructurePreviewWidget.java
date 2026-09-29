package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class StructurePreviewWidget extends UIElement implements ILocalUI {

    public static final int WIDTH = 160;
    private static final int SCENE_HEIGHT = 104;
    private static final int CONFIG_HEIGHT = 70;
    private static final int PARTS_HEIGHT = 22;
    public static final int HEIGHT = SCENE_HEIGHT + CONFIG_HEIGHT + PARTS_HEIGHT + 2 * UISizes.GAP;
    private static final int SCENE_DELAY_FRAMES = 3;

    private final MultiblockMachineDefinition definition;
    private final StructureScene scene;
    private final StructureConfigView config;
    private final DraggableScrollableWidgetGroup parts;
    private final Runnable onShown;
    private boolean shown;
    private int drawnFrames;
    @Nullable
    private StructurePlans.Preview preview;
    private boolean sceneDirty;
    private boolean needsRebuild = true;

    public StructurePreviewWidget(MultiblockMachineDefinition definition, Structure structure, Runnable onShown) {
        this.definition = definition;
        this.onShown = onShown;
        setClientSideWidget();
        layout(l -> l.column().size(WIDTH, HEIGHT).gapAll(UISizes.GAP));
        setSize(new Size(WIDTH, HEIGHT));
        scene = new StructureScene(WIDTH, SCENE_HEIGHT, false);
        scene.setReloader(() -> sceneDirty = true);
        var stage = new WidgetGroup(0, 0, WIDTH, SCENE_HEIGHT);
        stage.addWidget(scene);
        stage.addWidget(new ImageWidget(3, 3, WIDTH - 6, 10, new TextTexture(definition.getDescriptionId(), -1)
                .setType(TextTexture.TextType.ROLL).setWidth(WIDTH - 20).setDropShadow(true)));
        var expand = Button.icon(UITheme.CANVAS_FIT).setOnClientClick(onShown);
        expand.setSelfPosition(new Position(WIDTH - Button.ICON_SIZE - 2, 2));
        expand.setHoverTooltips(StructurePreviewScreen.OPEN);
        stage.addWidget(expand);
        config = new StructureConfigView(definition, structure, StructureBuildFlow.remembered(definition, structure), null, () -> needsRebuild = true, null, WIDTH, WIDTH, CONFIG_HEIGHT);
        var configArea = new UIElement().layout(l -> l.column().size(WIDTH, CONFIG_HEIGHT)).addChild(config);
        parts = new DraggableScrollableWidgetGroup(0, 0, WIDTH, PARTS_HEIGHT)
                .setXScrollBarHeight(4)
                .setXBarStyle(GuiTextures.SLIDER_BACKGROUND, GuiTextures.BUTTON)
                .setScrollable(true)
                .setDraggable(true);
        parts.setScrollWheelDirection(DraggableScrollableWidgetGroup.ScrollWheelDirection.HORIZONTAL);
        addChildren(stage, configArea, parts);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!shown) {
            shown = true;
            onShown.run();
        } else if (needsRebuild) {
            rebuild();
        }
        if (drawnFrames < SCENE_DELAY_FRAMES) drawnFrames++;
        else if (sceneDirty) showScene();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    private void showScene() {
        sceneDirty = false;
        if (preview == null) return;
        var layout = preview.layout();
        scene.show(preview.blocks(), StructureScene.ALL_LAYERS, StructureScene.fitZoom(layout.width(), layout.height(), layout.depth()));
    }

    private void rebuild() {
        needsRebuild = false;
        var layout = config.currentLayout();
        if (layout == null) return;
        preview = StructurePlans.preview(definition, layout);
        sceneDirty = true;
        parts.clearAllWidgets();
        var slots = PartSlots.create(preview.parts());
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            slot.setSelfPosition(new Position(2 + i * UISizes.SLOT, 0));
            parts.addWidget(slot);
        }
    }
}
