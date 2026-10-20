package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.core.ILevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import appeng.api.storage.MEStorage;
import com.suried.gtoutils.spatial.BlockIndex;
import com.suried.gtoutils.spatial.BlockRange;
import com.gto.datasynclib.datastream.DataComponentKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 世界级 {@link MEStorageProvider} 索引：每个 {@link Level} 一棵 {@link BlockOctree}，按位置索引容器
 * 机器，使用方（合成站等）按范围直接查，不需要逐格扫描方块。
 *
 * <p>
 * 存储方式遵循仓库规范：挂在 {@link ILevel} 的 {@code DataComponentMap} capability 上，随 Level 实例
 * 一起创建/回收，**不用** SavedData、静态 Map，也不依赖 Level 卸载回调清理。
 *
 * <p>
 * 条目维护：容器机器在 {@code onLoad} 里 {@link #add}、在 {@code onUnload}/{@code onMachineRemoved}
 * 里 {@link #remove}——这是区块级生命周期（机器被卸载后不该继续被查到），与上面的 Level 级生命周期
 * 是两件事。
 *
 * <p>
 * 线程模型：只在服务端主线程使用（机器生命周期、机器 tick、界面按钮），不做同步。
 */
public final class MEStorageProviderIndex {

    private static final DataComponentKey<MEStorageProviderIndex> KEY = DataComponentKey
            .createNoCodec("gtceu_me_storage_providers");

    private final Level level;
    private final BlockIndex<MEStorageProvider> index = new BlockIndex<>();

    private MEStorageProviderIndex(Level level) {
        this.level = level;
    }

    /** 取该 Level 的索引，没有则创建并挂到 Level capability 上。 */
    public static MEStorageProviderIndex get(Level level) {
        MEStorageProviderIndex index = ILevel.getCapability(level, KEY);
        if (index == null) {
            index = new MEStorageProviderIndex(level);
            ILevel.setCapability(level, KEY, index);
        }
        return index;
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
                index.remove(provider.getPos());
            }
        }
    }

    /** 只读：没有索引时返回 null，不创建。 */
    public static @Nullable MEStorageProviderIndex getIfPresent(Level level) {
        return ILevel.getCapability(level, KEY);
    }

    public Level level() {
        return level;
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
