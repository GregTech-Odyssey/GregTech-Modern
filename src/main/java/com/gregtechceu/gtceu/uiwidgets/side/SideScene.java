package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.structure.StructureScene;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class SideScene {

    private static final MachineSide[] SIDES = MachineSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float ZOOM = 2.2f;
    private static final float GHOST_ALPHA = 0.3f;
    private static final float ELEVATION = 0.5f;
    private static final float SKEW = 0.35f;
    private static final float OVERVIEW_SIDE = 0.6f;
    private static final float OVERVIEW_UP = 0.6f;
    private static final int GHOST_LAYER = 1;
    private static final float OUTPUT_INNER_INSET = 0.07f;
    private static final float MARK_INSET = 0.16f;
    private static final int MARK_FILL_ALPHA = 0x30000000;

    private final SideOverview page;
    private final StructureScene view;
    private boolean dirty;
    private boolean loaded;

    SideScene(SideOverview page, int width, int height) {
        this.page = page;
        this.view = new StructureScene("side_overview", width, height, false);
        view.setSelectionBox(false);
        view.setOrbit(true);
        view.setFacePainter(this::paint);
        view.setTooltip(this::tooltip);
        view.setOnSelected(this::onSelected);
        view.setReloader(this::load);
        reload();
    }

    StructureScene getView() {
        return view;
    }

    void reload() {
        if (dirty) return;
        dirty = true;
        Minecraft.getInstance().tell(this::loadIfDirty);
    }

    private void loadIfDirty() {
        if (dirty) load();
    }

    private void load() {
        boolean first = !loaded;
        loaded = true;
        dirty = false;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var pos = page.machine.getPos();
        var blocks = new Long2ObjectOpenHashMap<BlockState>(1);
        var entities = new Long2ObjectOpenHashMap<BlockEntity>(1);
        blocks.put(pos.asLong(), level.getBlockState(pos));
        var entity = level.getBlockEntity(pos);
        if (entity != null) entities.put(pos.asLong(), entity);
        view.showLive(blocks, entities, ZOOM);
        var ghosts = new Long2ObjectOpenHashMap<BlockState>(DIRECTIONS.length);
        for (var direction : DIRECTIONS) {
            var neighbor = pos.relative(direction);
            var state = level.getBlockState(neighbor);
            if (!state.isAir()) ghosts.put(neighbor.asLong(), state);
        }
        view.setOverlay(GHOST_LAYER, ghosts, 1, 1, 1, GHOST_ALPHA);
        if (first) lookOverview();
    }

    private void lookOverview() {
        var front = page.direction(MachineSide.FRONT).getNormal();
        var left = page.direction(MachineSide.LEFT).getNormal();
        var up = page.direction(MachineSide.TOP).getNormal();
        view.lookFrom(front.getX() + left.getX() * OVERVIEW_SIDE + up.getX() * OVERVIEW_UP,
                front.getY() + left.getY() * OVERVIEW_SIDE + up.getY() * OVERVIEW_UP,
                front.getZ() + left.getZ() * OVERVIEW_SIDE + up.getZ() * OVERVIEW_UP);
    }

    void lookAt(MachineSide side) {
        var normal = page.direction(side).getNormal();
        if (normal.getY() == 0) {
            view.lookFrom(normal.getX() - normal.getZ() * SKEW, ELEVATION, normal.getZ() + normal.getX() * SKEW);
            return;
        }
        var front = page.direction(MachineSide.FRONT);
        var across = (front.getAxis().isHorizontal() ? front : page.direction(MachineSide.TOP)).getNormal();
        view.lookFrom(across.getX() * ELEVATION, normal.getY(), across.getZ() * ELEVATION);
    }

    private void paint(StructureScene.FaceSink sink) {
        var pos = page.machine.getPos();
        int item = page.getItemOutput(), fluid = page.getFluidOutput();
        var itemFace = SideOverview.outputFace(item);
        var fluidFace = SideOverview.outputFace(fluid);
        if (itemFace != null) sink.face(pos, itemFace, outputColor(UITheme.SIDE_ITEM_OUTPUT, item), 0, 0);
        if (fluidFace != null) sink.face(pos, fluidFace, outputColor(UITheme.SIDE_FLUID_OUTPUT, fluid), 0, fluidFace == itemFace ? OUTPUT_INNER_INSET : 0);
        page.setSceneHover(hoveredSide(pos));
        int selected = page.getSelected(), highlighted = page.getHighlighted();
        if (highlighted >= 0 && highlighted != selected) {
            sink.face(pos, page.direction(SIDES[highlighted]), UITheme.SIDE_HOVER_FRAME, UITheme.SIDE_HOVER_FILL, MARK_INSET);
        }
        if (selected >= 0) {
            int color = UITheme.SELECTION_COLOR;
            sink.face(pos, page.direction(SIDES[selected]), color, (color & 0xFFFFFF) | MARK_FILL_ALPHA, MARK_INSET);
        }
    }

    private static int outputColor(int color, int state) {
        return SideOverview.isAuto(state) ? color : (color & 0xFFFFFF) | UITheme.SIDE_OUTPUT_IDLE_ALPHA;
    }

    private MachineSide hoveredSide(BlockPos pos) {
        var face = view.getHoverFace();
        return face != null && pos.equals(view.getHoverPos()) ? page.side(face) : null;
    }

    private List<Component> tooltip() {
        var side = hoveredSide(page.machine.getPos());
        return side == null ? Collections.emptyList() : page.tooltip(side);
    }

    private void onSelected(BlockPos pos, Direction face) {
        if (pos.equals(page.machine.getPos())) page.select(page.side(face));
    }
}
