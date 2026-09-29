package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StructurePreviewWidget extends UIElement implements ILocalUI {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 160;
    private static final int SCENE_DELAY_FRAMES = 3;

    private final MultiblockMachineDefinition definition;
    private final Structure structure;
    private final StructureScene scene;
    private int drawnFrames;
    private boolean sceneShown;

    public StructurePreviewWidget(MultiblockMachineDefinition definition, Structure structure, Runnable openFull) {
        this(definition, structure, WIDTH, HEIGHT, openFull);
    }

    public StructurePreviewWidget(MultiblockMachineDefinition definition, Structure structure, int width, int height, Runnable openFull) {
        this.definition = definition;
        this.structure = structure;
        setClientSideWidget();
        int sceneHeight = height - Button.HEIGHT - UISizes.GAP;
        layout(l -> l.column().size(width, height).gapAll(UISizes.GAP));
        setSize(new Size(width, height));
        scene = new StructureScene(width, sceneHeight, false);
        scene.setReloader(() -> sceneShown = false);
        var stage = new WidgetGroup(0, 0, width, sceneHeight);
        stage.addWidget(scene);
        stage.addWidget(new ImageWidget(3, 3, width - 6, 10, new TextTexture(definition.getDescriptionId(), -1)
                .setType(TextTexture.TextType.ROLL).setWidth(width - 6).setDropShadow(true)));
        var open = Button.translatable(width, StructurePreviewScreen.OPEN).setOnClientClick(openFull);
        addChildren(stage, open);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (drawnFrames < SCENE_DELAY_FRAMES) drawnFrames++;
        else if (!sceneShown) showScene();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    private void showScene() {
        sceneShown = true;
        var layout = structure.layout(StructureBuildFlow.remembered(definition, structure));
        if (layout == null) return;
        var preview = StructurePlans.preview(definition, layout);
        scene.show(preview.blocks(), StructureScene.ALL_LAYERS, StructureScene.fitZoom(layout.width(), layout.height(), layout.depth()));
    }
}
