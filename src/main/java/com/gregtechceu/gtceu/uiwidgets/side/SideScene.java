package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.structure.StructureScene;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

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
    private static final double PLAYER_VIEW_RANGE_SQR = 64;
    private static final int GHOST_LAYER = 1;
    private static final int MARK_RING = 1;
    private static final int ITEM_RING = 3;
    private static final int FLUID_RING = 4;

    private final SideOverview page;
    private final StructureScene view;
    private boolean dirty;
    private boolean loaded;
    private int renderKey;

    SideScene(SideOverview page, int width, int height) {
        this.page = page;
        this.view = new StructureScene("side_overview", width, height, false);
        view.setSelectionBox(false);
        view.setOrbit(true);
        view.setFacePainter(this::paint);
        view.setTooltip(this::tooltip);
        view.setOnFaceClick(this::onFaceClick);
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
        renderKey = renderKey();
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
        if (first) lookFromPlayer(level);
    }

    private int renderKey() {
        var machine = page.machine;
        int key = MachineSide.frameKey(machine) * 256 + page.getOutputKey();
        var covers = machine.getCoverContainer();
        for (var direction : DIRECTIONS) key = key * 31 + System.identityHashCode(covers.getCoverAtSide(direction));
        return key;
    }

    private void lookFromPlayer(Level level) {
        var player = Minecraft.getInstance().player;
        var center = Vec3.atCenterOf(page.machine.getPos());
        if (player == null || player.level() != level || player.distanceToSqr(center) > PLAYER_VIEW_RANGE_SQR) {
            lookOverview();
            return;
        }
        var eye = player.getEyePosition();
        view.lookFrom((float) (eye.x - center.x), (float) (eye.y - center.y), (float) (eye.z - center.z));
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
        if (loaded && !dirty && renderKey() != renderKey) reload();
        var pos = page.machine.getPos();
        int item = page.getItemOutput(), fluid = page.getFluidOutput();
        var itemFace = SideOverview.outputFace(item);
        if (itemFace != null) sink.ring(pos, itemFace, ITEM_RING, SideOverview.outputColor(UITheme.SIDE_ITEM_OUTPUT, item));
        var fluidFace = SideOverview.outputFace(fluid);
        if (fluidFace != null) sink.ring(pos, fluidFace, FLUID_RING, SideOverview.outputColor(UITheme.SIDE_FLUID_OUTPUT, fluid));
        page.setSceneHover(hoveredSide(pos));
        int selected = page.getSelected(), highlighted = page.getHighlighted();
        if (highlighted >= 0 && highlighted != selected) sink.ring(pos, page.direction(SIDES[highlighted]), MARK_RING, UITheme.SIDE_HOVER_FRAME);
        if (selected >= 0) sink.ring(pos, page.direction(SIDES[selected]), MARK_RING, UITheme.SIDE_SELECTED_FRAME);
    }

    @Nullable
    private MachineSide hoveredSide(BlockPos pos) {
        var face = view.getHoverFace();
        return face != null && pos.equals(view.getHoverPos()) ? page.side(face) : null;
    }

    private List<Component> tooltip() {
        var side = hoveredSide(page.machine.getPos());
        return side == null ? Collections.emptyList() : page.hoverLines(side);
    }

    private void onFaceClick(@Nullable BlockPos pos, @Nullable Direction face, int button) {
        if (pos == null) {
            if (button == 0) page.clientDeselect();
            return;
        }
        if (face != null && pos.equals(page.machine.getPos())) page.clientClick(page.side(face), button, false);
    }
}
