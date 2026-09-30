package com.gregtechceu.gtceu.uiwidgets.prospector;

import com.gregtechceu.gtceu.api.gui.misc.PacketProspecting;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.api.gui.texture.ProspectingTexture;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.common.item.ProspectorScannerBehavior;
import com.gregtechceu.gtceu.integration.map.WaypointManager;
import com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.canvas.CanvasItem;
import com.gregtechceu.gtceu.uipro.canvas.CanvasLayer;
import com.gregtechceu.gtceu.uipro.canvas.CanvasPainter;
import com.gregtechceu.gtceu.uipro.canvas.CanvasRect;
import com.gregtechceu.gtceu.uipro.canvas.CanvasView;
import com.gregtechceu.gtceu.uipro.canvas.ItemLayer;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.InfoIcon;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.view.ZoomBar;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProspectorMapView extends UIElement {

    private static final String SEARCH = "gtceu.prospector.ui.search";
    private static final String OUT_OF_RANGE = "gtceu.prospector.ui.out_of_range";
    private static final String HELP_WAYPOINT = "gtceu.prospector.ui.help.waypoint";
    private static final String HELP_FILTER = "gtceu.prospector.ui.help.filter";
    public static final int MAP_CHUNKS = 11;
    private static final int SIDE_WIDTH = 120;
    private static final int ROW_HEIGHT = 16;
    private static final int MAP_MARGIN = 3;
    private static final int MAP_SIZE = MAP_CHUNKS * 16 + 2 * (MAP_MARGIN + 1);
    private static final int HOVER_COLOR = 0x4B6CF76C;
    private static final int GRID_LIGHT = 0xFFB4B4B4;
    private static final int GRID_DARK = 0xFF505050;
    private static final float MAX_SCALE = 4;

    private final int chunkRadius;
    private final int scanSide;
    private final int displaySide;
    private final int scanOffset;
    private final ProspectorMode<?>[] modes;
    private final IntSupplier modeSource;
    private final BooleanSupplier darkSource;
    private int modeIndex;
    private ProspectorMode mode;
    private final MapCanvas canvas;
    private final ScrollerView list;
    private final Map<String, OreRow> rows = new LinkedHashMap<>();
    private final Map<String, Object> items = new LinkedHashMap<>();
    private final Queue<PacketProspecting> packets = new LinkedBlockingQueue<>();
    private String query = "";
    private String selected = ProspectingTexture.SELECTED_ALL;
    private boolean dark;
    private int playerChunkX, playerChunkZ, playerBlockX, playerBlockZ;
    private int chunkIndex;
    @Nullable
    @OnlyIn(Dist.CLIENT)
    private ProspectingTexture texture;
    private boolean textureLoaded;

    public ProspectorMapView(MachineWindow window, int chunkRadius, ProspectorMode<?>[] modes, IntSupplier modeSource, BooleanSupplier darkSource) {
        this.chunkRadius = chunkRadius;
        this.scanSide = chunkRadius * 2 - 1;
        this.displaySide = Math.max(scanSide, MAP_CHUNKS);
        this.scanOffset = (displaySide - scanSide) / 2;
        this.modes = modes;
        this.modeSource = modeSource;
        this.darkSource = darkSource;
        this.modeIndex = currentMode();
        this.mode = modes[modeIndex];
        this.dark = darkSource.getAsBoolean();
        int height = window.isRemote() ? Mth.clamp(MachineWindow.clientPageHeightLimit(false), UISizes.CANVAS_MIN_SIZE, MAP_SIZE) : MAP_SIZE;
        layout(l -> l.row().height(height).gapAll(UISizes.SECTION_GAP).alignCenter());

        canvas = new MapCanvas(height);
        var search = new TextField(SIDE_WIDTH, () -> query, this::search).setPlaceholder(() -> Component.translatable(SEARCH));
        list = new ScrollerView("prospector.list", SIDE_WIDTH, ROW_HEIGHT);
        list.setResizable(false);
        list.getLayoutStyle().flex(1);
        list.setBackground(UITheme.STATUS_PANEL);
        list.layoutContent(l -> l.paddingAll(UITheme.PANEL_PADDING));
        search.setClientSideWidget();
        list.setClientSideWidget();

        var side = UIElement.column(SIDE_WIDTH).layout(l -> l.height(height).gapAll(UISizes.GAP)).addChildren(
                new TextLine(SIDE_WIDTH, () -> Component.translatable(mode.unlocalizedName), Component.translatable(mode.unlocalizedName)),
                search, list);
        addChildren(canvas, side);
        addSyncValue(SyncValue.of(darkSource::getAsBoolean, ByteStreamCodec.BOOLEAN_CODEC, dark)).onChanged(this::setDark);
        addSyncValue(SyncValue.ofInt(() -> modeIndex, modeIndex)).onChanged(this::applyMode);

        window.addTitleTool(() -> ZoomBar.title(canvas));
        window.addTitleTool(() -> new InfoIcon(InfoIcon.Kind.INFO, Component.translatable(HELP_FILTER), Component.translatable(HELP_WAYPOINT),
                Component.translatable(CanvasView.HELP_PAN), Component.translatable(CanvasView.HELP_ZOOM)));
    }

    private int currentMode() {
        return Mth.clamp(modeSource.getAsInt(), 0, modes.length - 1);
    }

    private void search(String text) {
        query = text;
        String needle = text.toLowerCase(Locale.ROOT);
        for (var row : rows.values()) {
            row.setDisplay(needle.isEmpty() || row.searchText.contains(needle));
        }
    }

    private void select(String uid) {
        selected = uid.equals(selected) ? ProspectingTexture.SELECTED_ALL : uid;
        if (isRemote() && texture != null) texture.setSelected(selected);
    }

    private void setDark(boolean value) {
        dark = value;
        if (isRemote() && texture != null) texture.setDarkMode(value);
    }

    private void applyMode(int index) {
        modeIndex = Mth.clamp(index, 0, modes.length - 1);
        mode = modes[modeIndex];
        if (!isRemote()) return;
        packets.clear();
        items.clear();
        rows.clear();
        list.clearScrollViewChildren();
        selected = ProspectingTexture.SELECTED_ALL;
        search(query);
        createTexture();
    }

    @OnlyIn(Dist.CLIENT)
    private void createTexture() {
        if (texture != null) RenderSystem.recordRenderCall(texture::releaseId);
        texture = new ProspectingTexture(playerChunkX, playerChunkZ, playerBlockX, playerBlockZ,
                gui.entityPlayer.getVisualRotationYInDegrees(), mode, chunkRadius, dark);
        textureLoaded = false;
    }

    @OnlyIn(Dist.CLIENT)
    private void addItems(Object[][][] data) {
        for (int x = 0; x < mode.cellSize; x++) {
            for (int z = 0; z < mode.cellSize; z++) {
                for (var item : data[x][z]) {
                    String uid = mode.getUniqueID(item);
                    items.putIfAbsent(uid, item);
                    if (rows.containsKey(uid)) continue;
                    var row = new OreRow(uid, mode.getItemIcon(item), Component.translatable(mode.getDescriptionId(item)));
                    list.addScrollViewChild(row);
                    String needle = query.toLowerCase(Locale.ROOT);
                    if (!needle.isEmpty() && !row.searchText.contains(needle)) row.setDisplay(false);
                }
            }
        }
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        var player = gui.entityPlayer;
        buffer.writeVarInt(playerChunkX = player.chunkPosition().x);
        buffer.writeVarInt(playerChunkZ = player.chunkPosition().z);
        buffer.writeVarInt(playerBlockX = player.getBlockX());
        buffer.writeVarInt(playerBlockZ = player.getBlockZ());
        super.writeInitialData(buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readInitialData(FriendlyByteBuf buffer) {
        playerChunkX = buffer.readVarInt();
        playerChunkZ = buffer.readVarInt();
        playerBlockX = buffer.readVarInt();
        playerBlockZ = buffer.readVarInt();
        createTexture();
        super.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        int current = currentMode();
        if (current != modeIndex) {
            modeIndex = current;
            mode = modes[current];
            chunkIndex = 0;
        }
        super.detectAndSendChanges();
        scan();
    }

    private void scan() {
        var player = gui.entityPlayer;
        var level = player.level();
        var held = player.getItemInHand(InteractionHand.MAIN_HAND);
        while (chunkIndex < scanSide * scanSide) {
            int ox = chunkIndex % scanSide - chunkRadius + 1;
            int oz = chunkIndex / scanSide - chunkRadius + 1;
            var chunk = level.getChunk(playerChunkX + ox, playerChunkZ + oz);
            if (mode == ProspectorMode.ORE) {
                ServerCache.instance.prospectAllInChunk(level.dimension(), chunk.getPos(), (ServerPlayer) player);
            }
            var packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, mode);
            mode.scan(packet.data, chunk);
            writeUpdateInfo(-1, packet::writePacketData);
            chunkIndex++;
            if (!player.isCreative() && held.getItem() instanceof IComponentItem componentItem) {
                for (var component : componentItem.getComponents()) {
                    if (component instanceof ProspectorScannerBehavior prospector && !prospector.drainEnergy(held, false)) {
                        player.closeContainer();
                        return;
                    }
                }
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id != -1) {
            super.readUpdateInfo(id, buffer);
            return;
        }
        var packet = PacketProspecting.readPacketData(mode, buffer);
        if (Math.abs(packet.chunkX - playerChunkX) >= chunkRadius || Math.abs(packet.chunkZ - playerChunkZ) >= chunkRadius) return;
        packets.add(packet);
        var dimension = gui.entityPlayer.level().dimension();
        if (mode == ProspectorMode.FLUID && packet.data[0][0].length > 0) {
            GTClientCache.instance.addFluid(dimension, packet.chunkX, packet.chunkZ, (ProspectorMode.FluidInfo) packet.data[0][0][0]);
        } else if (mode == ProspectorMode.BEDROCK_ORE && packet.data[0][0].length > 0) {
            GTClientCache.instance.addBedrockOre(dimension, packet.chunkX, packet.chunkZ, (ProspectorMode.OreInfo[]) packet.data[0][0]);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        while (!packets.isEmpty()) {
            var packet = packets.poll();
            if (texture != null) {
                texture.updateTexture(packet);
                textureLoaded = true;
            }
            addItems(packet.data);
        }
    }

    private boolean inScan(int cx, int cz) {
        return cx >= scanOffset && cz >= scanOffset && cx < scanOffset + scanSide && cz < scanOffset + scanSide;
    }

    private final class OreRow extends UIElement {

        private final String uid;
        private final String searchText;

        private OreRow(String uid, IGuiTexture icon, Component name) {
            this.uid = uid;
            this.searchText = (name.getString() + " " + uid).toLowerCase(Locale.ROOT);
            var text = TextLine.constant(0, name);
            text.layout(l -> l.flex(1));
            layout(l -> l.row().height(ROW_HEIGHT).gapAll(UISizes.GAP).alignCenter());
            addChildren(new ItemView(icon, ROW_HEIGHT), text);
            setSelected(() -> uid.equals(selected));
            rows.put(uid, this);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOverElement(mouseX, mouseY)) {
                select(uid);
                playButtonClickSound();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (isMouseOverElement(mouseX, mouseY)) {
                RenderSystem.colorMask(true, true, true, false);
                graphics.fill(getPositionX(), getPositionY(), getPositionX() + getSizeWidth(), getPositionY() + getSizeHeight(), UITheme.SLOT_HOVER_OVERLAY);
                RenderSystem.colorMask(true, true, true, true);
            }
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }

    private final class MapCanvas extends CanvasView {

        private MapCanvas(int size) {
            super("prospector.map", size, size);
            setResizable(false);
            setRememberView(false);
            setGrid(null);
            setClampInside(true);
            setFitPadding(0);
            setLodThresholds(0, 0);
            setScaleRange(0.01f, MAX_SCALE);
            setMinScaleFits(true);
            setInitialView(view -> view.fitContent(false));
            setScene(view -> {
                var cells = new ItemLayer<ChunkCell>();
                for (int cz = 0; cz < displaySide; cz++) {
                    for (int cx = 0; cx < displaySide; cx++) cells.add(new ChunkCell(cx, cz, inScan(cx, cz)));
                }
                view.addLayer(new MapLayer()).addLayer(cells);
            });
            setOnItemClick((item, button, worldX, worldZ) -> {
                if (button == 0 && item instanceof ChunkCell cell && cell.inScan) addWaypoint(worldX, worldZ);
            });
        }

        @OnlyIn(Dist.CLIENT)
        private void addWaypoint(float worldX, float worldZ) {
            if (!WaypointManager.isActive() || texture == null) return;
            int localX = Mth.floor(worldX) - scanOffset * 16, localZ = Mth.floor(worldZ) - scanOffset * 16;
            if (localX < 0 || localZ < 0 || localX >= scanSide * 16 || localZ >= scanSide * 16) return;
            var waypoint = waypointAt(localX, localZ);
            MutableComponent veinName = Component.literal(waypoint.name());
            veinName.setStyle(veinName.getStyle().withColor(waypoint.color()));
            var player = gui.entityPlayer;
            WaypointManager.setWaypoint(new ChunkPos(waypoint.position()).toString(), waypoint.name(), waypoint.color(), player.level().dimension(),
                    waypoint.position().getX(), waypoint.position().getY(), waypoint.position().getZ());
            player.displayClientMessage(Component.translatable("behavior.prospector.added_waypoint", veinName), false);
        }

        @OnlyIn(Dist.CLIENT)
        private Waypoint waypointAt(int localX, int localZ) {
            int cx = localX >> 4, cz = localZ >> 4;
            int offsetX = localX & 15, offsetZ = localZ & 15;
            int xPos = ((playerChunkX + cx - (chunkRadius - 1)) << 4) + offsetX;
            int zPos = ((playerChunkZ + cz - (chunkRadius - 1)) << 4) + offsetZ;
            var level = gui.entityPlayer.level();
            var pos = new BlockPos(xPos, level.getHeight(Heightmap.Types.WORLD_SURFACE, xPos, zPos), zPos);
            if (!texture.getSelected().equals(ProspectingTexture.SELECTED_ALL)) {
                var item = items.get(texture.getSelected());
                if (item != null) {
                    return new Waypoint(pos, Component.translatable(mode.getDescriptionId(item)).getString(), mode.getItemColor(item));
                }
            }
            var hovered = texture.data[cx * mode.cellSize + offsetX * mode.cellSize / 16][cz * mode.cellSize + offsetZ * mode.cellSize / 16];
            if (hovered != null && hovered.length != 0) {
                return new Waypoint(pos, Component.translatable(mode.getDescriptionId(hovered[0])).getString(), mode.getItemColor(hovered[0]));
            }
            var veins = GTClientCache.instance.getNearbyVeins(level.dimension(), pos, 32);
            if (!veins.isEmpty()) {
                veins.sort((a, b) -> (int) (a.center().distToCenterSqr(xPos, a.center().getY(), zPos) - b.center().distToCenterSqr(xPos, b.center().getY(), zPos)));
                var vein = veins.getFirst();
                return new Waypoint(pos, OreRenderLayer.getName(vein).getString(), vein.definition().veinGenerator().getAllMaterials().getLast().getMaterialRGB());
            }
            return new Waypoint(pos, "Depleted Vein", 10027008);
        }
    }

    private final class MapLayer implements CanvasLayer {

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(CanvasPainter painter, @Nullable CanvasItem hovered) {
            int size = displaySide * 16;
            painter.fill(0, 0, size, size, dark ? ColorPattern.GRAY.color : ColorPattern.WHITE.color);
            painter.flush();
            var graphics = painter.graphics();
            if (texture != null) {
                if (!textureLoaded) {
                    texture.load();
                    textureLoaded = true;
                }
                texture.draw(graphics, scanOffset * 16, scanOffset * 16);
            }
            int line = dark ? GRID_DARK : GRID_LIGHT;
            for (int i = 0; i < displaySide; i++) {
                painter.fill(i * 16, 0, i * 16 + 1, size, line);
                painter.fill(0, i * 16, size, i * 16 + 1, line);
            }
            painter.flush();
            for (int cz = 0; cz < displaySide; cz++) {
                for (int cx = 0; cx < displaySide; cx++) {
                    if (!inScan(cx, cz)) UITheme.drawDisabled(graphics, cx * 16, cz * 16, 17, 17);
                }
            }
        }

        @Override
        public CanvasRect bounds() {
            return CanvasRect.of(0, 0, displaySide * 16, displaySide * 16);
        }
    }

    private final class ChunkCell implements CanvasItem {

        private final int cx, cz;
        private final boolean inScan;
        private final CanvasRect bounds;

        private ChunkCell(int cx, int cz, boolean inScan) {
            this.cx = cx;
            this.cz = cz;
            this.inScan = inScan;
            this.bounds = CanvasRect.of(cx * 16, cz * 16, 16, 16);
        }

        @Override
        public CanvasRect bounds() {
            return bounds;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawShape(CanvasPainter painter, boolean hovered) {
            if (hovered && inScan) painter.fill(bounds, HOVER_COLOR);
        }

        @Override
        public List<Component> tooltip() {
            if (!inScan) return Collections.singletonList(Component.translatable(OUT_OF_RANGE));
            List<Component> tooltips = new ArrayList<>();
            tooltips.add(Component.translatable(mode.unlocalizedName));
            if (texture != null) {
                int x = cx - scanOffset, z = cz - scanOffset;
                List<Object[]> cell = new ArrayList<>();
                for (int i = 0; i < mode.cellSize; i++) {
                    for (int j = 0; j < mode.cellSize; j++) {
                        var entry = texture.data[x * mode.cellSize + i][z * mode.cellSize + j];
                        if (entry != null) cell.add(entry);
                    }
                }
                mode.appendTooltips(cell, tooltips, texture.getSelected());
            }
            return tooltips;
        }
    }

    private record Waypoint(BlockPos position, String name, int color) {}

    public static boolean isOpen(ModularUI ui) {
        for (var widget : ui.getFlatVisibleWidgetCollection()) {
            if (widget instanceof ProspectorMapView) return true;
        }
        return false;
    }
}
