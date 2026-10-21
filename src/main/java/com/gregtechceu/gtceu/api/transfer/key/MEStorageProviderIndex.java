package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import appeng.api.storage.MEStorage;
import com.suried.gtoutils.spatial.BlockIndex;
import com.suried.gtoutils.spatial.BlockRange;
import com.suried.gtoutils.spatial.LevelStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 世界级 {@link MEStorageProvider} 索引：按位置索引容器
 * 机器，使用方（合成站等）按范围直接查，不需要逐格扫描方块。
 *
 * <p>
 * 条目维护：容器机器在 {@code onLoad} 里 {@link #add}、在 {@code onUnload}/{@code onMachineRemoved}
 * 里 {@link #remove}——这是区块级生命周期（机器被卸载后不该继续被查到）。
 *
 * <p>
 * 线程模型：只在服务端主线程使用（机器生命周期、机器 tick、界面按钮），不做同步。
 */
public final class MEStorageProviderIndex {

    private static final LevelStore<MEStorageProviderIndex> STORE = LevelStore.create(MEStorageProviderIndex::new);

    private final BlockIndex<MEStorageProvider> index = new BlockIndex<>();

    private MEStorageProviderIndex() {}

    public static MEStorageProviderIndex get(Level level) {
        return STORE.get(level);
    }

    public static void add(Level level, MEStorageProvider provider) {
        if (level instanceof ServerLevel serverLevel) {
            MEStorageProviderIndex.get(serverLevel).add(provider);
        }
    }

    public static void remove(Level level, MEStorageProvider provider) {
        if (level instanceof ServerLevel serverLevel) {
            var index = getIfPresent(serverLevel);
            if (index != null) {
                index.index.remove(provider.getPos(), provider);
            }
        }
    }

    /** 只读：没有索引时返回 null，不创建。 */
    public static @Nullable MEStorageProviderIndex getIfPresent(Level level) {
        return STORE.getIfPresent(level);
    }

    public int version() {
        return index.version();
    }

    public int size() {
        return index.size();
    }

    /** 注册（同一位置重复注册会替换旧实例）。 @return 是否发生变化 */
    public boolean add(MEStorageProvider provider) {
        int before = index.version();
        return index.put(provider.getPos(), provider) && index.version() != before;
    }

    /** 注销。 @return 是否确实移除了一项 */
    public boolean remove(BlockPos pos) {
        return index.remove(pos) != null;
    }

    /**
     * 球形范围查询（方块中心欧氏距离 ≤ radius）。
     *
     * @return 找到的数量
     */
    public int query(int x, int y, int z, int radius, Collection<? super MEStorageProvider> out) {
        return index.query(x, y, z, BlockRange.sphere(radius), null, out);
    }

    /** 便利方法：返回范围内的容器。会新建列表，别在热路径每 tick 调用。 */
    public List<MEStorageProvider> query(int x, int y, int z, int radius) {
        ObjectArrayList<MEStorageProvider> out = new ObjectArrayList<>();
        query(x, y, z, radius, out);
        return out;
    }

    /** 该容器的 {@link MEStorage} 视图，读不到时返回 null。 */
    public static @Nullable MEStorage storageOf(@Nullable MEStorageProvider provider) {
        return provider == null ? null : provider.getStorageCap(null);
    }
}
