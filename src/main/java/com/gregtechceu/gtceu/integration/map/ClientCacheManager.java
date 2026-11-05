package com.gregtechceu.gtceu.integration.map;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.integration.map.cache.client.IClientCache;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.gto.fastcollection.fastutil.OpenCacheHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ClientCacheManager {

    public static final File clientCacheDir = new File(Minecraft.getInstance().gameDirectory, GTCEu.MOD_ID + File.separator + "prospection_cache");
    private static final char resourceLocationSeparator = '=';
    private static final String filePrefix = "DIM";
    private static final String fileEnding = ".nbt";
    private static final Pattern PATTERN = Pattern.compile("(?U)[^\\w-]+");
    private static File worldFolder;
    private static final Reference2ObjectMap<IClientCache, ClientCacheInfo> caches = new Reference2ObjectArrayMap<>();

    /**
     * 缓存文件的格式版本。条目按矿脉定义重建，版本不符时不做整体迁移：缺字段的旧条目在各自的
     * {@code fromNbt} 里自行丢弃，这个版本号只用于区分「旧格式」与「文件损坏」，以及后续升级的切入点。
     */
    public static final int NBT_VERSION = 1;
    private static final String NBT_VERSION_KEY = "version";
    private static boolean shouldInit = true;

    private static String sanitizeFilename(String input) {
        return PATTERN.matcher(input).replaceAll("_").trim();
    }

    public static void init(String worldId) {
        if (shouldInit) {
            final Player player = Minecraft.getInstance().player;
            worldFolder = new File(clientCacheDir, sanitizeFilename(player.getName().getString()) + "_" + player.getUUID() + File.separator + sanitizeFilename(worldId));
            worldFolder.mkdirs();
            // to ensure any cache data that might somehow be lying around gets dealt with
            clearCaches();
            loadCaches();
            // the map mod may still hold highlights computed from the previous contents of these caches
            GroupingMapRenderer.getInstance().refreshMarkers();
            shouldInit = false;
        }
    }

    private static void loadCaches() {
        for (IClientCache cache : caches.keySet()) {
            cache.setupCacheFiles();
            ClientCacheInfo cacheInfo = caches.get(cache);
            cacheInfo.cacheFolder = new File(worldFolder, cacheInfo.key);
            cacheInfo.cacheFolder.mkdirs();
            for (String dimFilePrefix : cacheInfo.dimFilePrefixes) {
                for (File dimFile : getDimFiles(cacheInfo.cacheFolder, dimFilePrefix)) {
                    ResourceKey<Level> dimId = ResourceKey.create(Registries.DIMENSION, ResourceLocation.of(dimFile.getName().substring(dimFilePrefix.length() + filePrefix.length(), dimFile.getName().length() - fileEnding.length()), resourceLocationSeparator));
                    try {
                        CompoundTag data = NbtIo.readCompressed(new FileInputStream(dimFile));
                        checkVersion(dimFile.getName(), data);
                        cache.readDimFile(dimFilePrefix, dimId, data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            for (String singleFileName : cacheInfo.singleFiles) {
                File singleFile = new File(cacheInfo.cacheFolder, singleFileName + fileEnding);
                if (!singleFile.exists()) continue;
                try {
                    CompoundTag data = NbtIo.readCompressed(new FileInputStream(singleFile));
                    checkVersion(singleFile.getName(), data);
                    cache.readSingleFile(singleFileName, data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void clearCaches() {
        for (IClientCache cache : caches.keySet()) {
            cache.clear();
        }
        GroupingMapRenderer.getInstance().clear();
    }

    public static void saveCaches() {
        for (IClientCache cache : caches.keySet()) {
            ClientCacheInfo cacheInfo = caches.get(cache);
            for (String dimFilePrefix : cacheInfo.dimFilePrefixes) {
                for (ResourceKey<Level> dim : cache.getExistingDimensions(dimFilePrefix)) {
                    CompoundTag data = cache.saveDimFile(dimFilePrefix, dim);
                    if (data == null) continue;
                    stampVersion(data);
                    File dimFile = new File(cacheInfo.cacheFolder, dimFilePrefix + filePrefix + dim.location().getNamespace() + "=" + dim.location().getPath() + fileEnding);
                    try {
                        NbtIo.writeCompressed(data, new FileOutputStream(dimFile));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            for (String singleFileName : cacheInfo.singleFiles) {
                CompoundTag data = cache.saveSingleFile(singleFileName);
                if (data == null) continue;
                stampVersion(data);
                File singleFile = new File(cacheInfo.cacheFolder, singleFileName + fileEnding);
                try {
                    NbtIo.writeCompressed(data, new FileOutputStream(singleFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void stampVersion(CompoundTag data) {
        data.putInt(NBT_VERSION_KEY, NBT_VERSION);
    }

    private static void checkVersion(String fileName, CompoundTag data) {
        int version = data.getInt(NBT_VERSION_KEY);
        if (version == NBT_VERSION) return;
        // 旧缓存没有版本号（0），条目在各自的 fromNbt 里按缺失字段逐条丢弃；这里只留一条可诊断的记录
        GTCEu.LOGGER.debug("Map cache {} uses format version {}, current is {}", fileName, version, NBT_VERSION);
    }

    public static void resetCaches() {
        clearCaches();
        for (ClientCacheInfo cacheInfo : caches.values()) {
            FileUtils.deleteQuietly(cacheInfo.cacheFolder);
            cacheInfo.cacheFolder.mkdirs();
        }
    }

    public static void registerClientCache(IClientCache cache, String key) {
        caches.put(cache, new ClientCacheInfo(key));
    }

    public static void addDimFiles(IClientCache cache, String prefix) {
        caches.get(cache).dimFilePrefixes.add(prefix);
    }

    public static void addSingleFile(IClientCache cache, String prefix) {
        caches.get(cache).singleFiles.add(prefix);
    }

    public static void allowReinit() {
        shouldInit = true;
    }

    private static List<File> getDimFiles(File parent, String prefix) {
        try (var stream = Files.walk(parent.toPath(), 1)) {
            return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith(prefix + filePrefix)).map(Path::toFile).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ClientCacheInfo {

        public String key;
        public File cacheFolder;
        public Set<String> dimFilePrefixes;
        public Set<String> singleFiles;

        public ClientCacheInfo(String key) {
            this.key = key;
            dimFilePrefixes = new OpenCacheHashSet<>();
            singleFiles = new OpenCacheHashSet<>();
        }
    }

    public static File getWorldFolder() {
        return ClientCacheManager.worldFolder;
    }
}
