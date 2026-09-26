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
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProspectorMapView extends UIElement {

    private static final String ALL = "gtceu.prospector.ui.all";
    private static final String SEARCH = "gtceu.prospector.ui.search";
    private static final String DARK_MAP = "gtceu.prospector.ui.dark_map";
    private static final int SIDE_WIDTH = 120;
    private static final int ROW_HEIGHT = 16;
    private static final int FRAME = 4;

    private final int chunkRadius;
    private final ProspectorMode mode;
    private final MapCanvas canvas;
    private final ScrollerView list;
    private final Map<String, OreRow> rows = new LinkedHashMap<>();
    private final Map<String, Object> items = new LinkedHashMap<>();
    private String query = "";
    private String selected = ProspectingTexture.SELECTED_ALL;
    private boolean dark;

    public ProspectorMapView(int chunkRadius, ProspectorMode<?> mode) {
        this.chunkRadius = chunkRadius;
        this.mode = mode;
        int mapBox = (chunkRadius * 2 - 1) * 16 + 2 * FRAME;
        int height = Math.max(mapBox, UISizes.MACHINE_PAGE_HEIGHT);
        layout(l -> l.row().height(height).gapAll(UISizes.SECTION_GAP).alignCenter());

        canvas = new MapCanvas(mapBox);
        var search = new TextField(SIDE_WIDTH, () -> query, this::search).setPlaceholder(() -> Component.translatable(SEARCH));
        list = new ScrollerView("prospector.list", SIDE_WIDTH, ROW_HEIGHT);
        list.setResizable(false);
        list.getLayoutStyle().flex(1);
        list.setBackground(UITheme.STATUS_PANEL);
        list.layoutContent(l -> l.paddingAll(UITheme.PANEL_PADDING));
        list.addScrollViewChild(new OreRow(ProspectingTexture.SELECTED_ALL, IGuiTexture.EMPTY, Component.translatable(ALL)));
        search.setClientSideWidget();
        list.setClientSideWidget();

        var side = UIElement.column(SIDE_WIDTH).layout(l -> l.height(height).gapAll(UISizes.GAP)).addChildren(
                TextLine.constant(SIDE_WIDTH, Component.translatable(mode.unlocalizedName)),
                CoverUIs.controlRow(DARK_MAP, Switch.of(() -> dark, value -> dark = value)),
                search, list);
        addChildren(canvas, side);
        addSyncValue(SyncValue.of(() -> dark, SyncValue.BOOLEAN, false)).onChanged(canvas::setDark);
    }

    private void search(String text) {
        query = text;
        String needle = text.toLowerCase(Locale.ROOT);
        for (var row : rows.values()) {
            row.setDisplay(needle.isEmpty() || row.uid.equals(ProspectingTexture.SELECTED_ALL) || row.searchText.contains(needle));
        }
    }

    private void select(String uid) {
        selected = uid;
        canvas.setSelected(uid);
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

    private final class MapCanvas extends Widget {

        private final int image;
        private final Queue<PacketProspecting> packets = new LinkedBlockingQueue<>();
        @Nullable
        @OnlyIn(Dist.CLIENT)
        private ProspectingTexture texture;
        private int playerChunkX, playerChunkZ;
        private int chunkIndex;

        private MapCanvas(int size) {
            super(0, 0, size, size);
            this.image = (chunkRadius * 2 - 1) * 16;
        }

        private void setDark(boolean value) {
            if (isRemote() && texture != null) texture.setDarkMode(value);
        }

        private void setSelected(String uid) {
            if (isRemote() && texture != null) texture.setSelected(uid);
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            super.writeInitialData(buffer);
            var player = gui.entityPlayer;
            buffer.writeVarInt(playerChunkX = player.chunkPosition().x);
            buffer.writeVarInt(playerChunkZ = player.chunkPosition().z);
            buffer.writeVarInt(player.getBlockX());
            buffer.writeVarInt(player.getBlockZ());
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void readInitialData(FriendlyByteBuf buffer) {
            super.readInitialData(buffer);
            playerChunkX = buffer.readVarInt();
            playerChunkZ = buffer.readVarInt();
            texture = new ProspectingTexture(playerChunkX, playerChunkZ, buffer.readVarInt(), buffer.readVarInt(),
                    gui.entityPlayer.getVisualRotationYInDegrees(), mode, chunkRadius, dark);
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();
            var player = gui.entityPlayer;
            var level = player.level();
            var held = player.getItemInHand(InteractionHand.MAIN_HAND);
            int side = (chunkRadius << 1) - 1;
            while (chunkIndex < side * side) {
                int ox = chunkIndex % side - chunkRadius + 1;
                int oz = chunkIndex / side - chunkRadius + 1;
                var chunk = level.getChunk(playerChunkX + ox, playerChunkZ + oz);
                if (mode == ProspectorMode.ORE) {
                    ServerCache.instance.prospectAllInChunk(level.dimension(), chunk.getPos(), (ServerPlayer) player);
                }
                var packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, mode);
                mode.scan(packet.data, chunk);
                writeUpdateInfo(-1, packet::writePacketData);
                chunkIndex++;
                if (held.getItem() instanceof IComponentItem componentItem) {
                    for (var component : componentItem.getComponents()) {
                        if (component instanceof ProspectorScannerBehavior prospector && !player.isCreative() && !prospector.drainEnergy(held, false)) {
                            player.closeContainer();
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
                if (texture != null) texture.updateTexture(packet);
                addItems(packet.data);
            }
        }

        private int imageX() {
            return getPositionX() + FRAME;
        }

        private int imageY() {
            return getPositionY() + FRAME;
        }

        private int hoveredChunk(double mouse, int origin) {
            return mouse < origin ? -1 : (int) (mouse - origin) / 16;
        }

        private boolean inMap(int cx, int cz) {
            return cx >= 0 && cz >= 0 && cx < chunkRadius * 2 - 1 && cz < chunkRadius * 2 - 1;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            UITheme.STATUS_PANEL.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
            int x = imageX(), y = imageY();
            if (texture != null) texture.draw(graphics, x, y);
            int cx = hoveredChunk(mouseX, x), cz = hoveredChunk(mouseY, y);
            if (inMap(cx, cz)) graphics.fill(x + cx * 16, y + cz * 16, x + cx * 16 + 16, y + cz * 16 + 16, 0x4B6CF76C);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = imageX(), y = imageY();
            int cx = hoveredChunk(mouseX, x), cz = hoveredChunk(mouseY, y);
            if (!inMap(cx, cz) || !isMouseOverElement(mouseX, mouseY)) return;
            List<Component> tooltips = new ArrayList<>();
            tooltips.add(Component.translatable(mode.unlocalizedName));
            if (texture != null) {
                List<Object[]> cell = new ArrayList<>();
                for (int i = 0; i < mode.cellSize; i++) {
                    for (int j = 0; j < mode.cellSize; j++) {
                        var entry = texture.data[cx * mode.cellSize + i][cz * mode.cellSize + j];
                        if (entry != null) cell.add(entry);
                    }
                }
                mode.appendTooltips(cell, tooltips, texture.getSelected());
            }
            gui.getModularUIGui().setHoverTooltip(tooltips, ItemStack.EMPTY, null, null);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (texture == null || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
            var waypoint = waypointAt(mouseX, mouseY);
            if (waypoint == null) return super.mouseClicked(mouseX, mouseY, button);
            if (!WaypointManager.isActive()) return true;
            MutableComponent veinName = Component.literal(waypoint.name());
            veinName.setStyle(veinName.getStyle().withColor(waypoint.color()));
            var player = gui.entityPlayer;
            WaypointManager.setWaypoint(new ChunkPos(waypoint.position()).toString(), waypoint.name(), waypoint.color(), player.level().dimension(),
                    waypoint.position().getX(), waypoint.position().getY(), waypoint.position().getZ());
            player.displayClientMessage(Component.translatable("behavior.prospector.added_waypoint", veinName), false);
            playButtonClickSound();
            return true;
        }

        @Nullable
        @OnlyIn(Dist.CLIENT)
        private Waypoint waypointAt(double mouseX, double mouseY) {
            int x = imageX(), y = imageY();
            int cx = hoveredChunk(mouseX, x), cz = hoveredChunk(mouseY, y);
            if (!inMap(cx, cz) || texture == null) return null;
            int offsetX = (int) (mouseX - x) % 16;
            int offsetZ = (int) (mouseY - y) % 16;
            var player = gui.entityPlayer;
            int xPos = ((player.chunkPosition().x + cx - (chunkRadius - 1)) << 4) + offsetX;
            int zPos = ((player.chunkPosition().z + cz - (chunkRadius - 1)) << 4) + offsetZ;
            var level = player.level();
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

    private record Waypoint(BlockPos position, String name, int color) {}

    public static boolean isOpen(ModularUI ui) {
        for (var widget : ui.getFlatVisibleWidgetCollection()) {
            if (widget instanceof ProspectorMapView) return true;
        }
        return false;
    }
}
