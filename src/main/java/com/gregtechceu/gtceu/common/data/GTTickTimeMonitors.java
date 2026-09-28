package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;

import com.gto.datasynclib.datastream.DataComponentRegistry;

public class GTTickTimeMonitors {

    public static final DataComponentRegistry REGISTRY = TickTimeMonitor.REGISTRY;

    static {
        REGISTRY.unfreeze();
    }

    /** 配方逻辑的 tick。 */
    public static final TickTimeMonitor.Entry RECIPE_LOGIC = TickTimeMonitor.create("recipe_logic", RecipeLogic.SEARCH_MAX_INTERVAL);
    /** 管道自身的传输（cover 不用：同一个方块实体上可以挂多个 cover，按 entry 缓存的监控器会被顶掉）。 */
    public static final TickTimeMonitor.Entry TRANSFER = TickTimeMonitor.create("transfer");
    /** 仓（总线、输入输出仓）的自动输入输出。 */
    public static final TickTimeMonitor.Entry AUTO_IO = TickTimeMonitor.create("auto_io");
    /** 机器的自动输出。 */
    public static final TickTimeMonitor.Entry AUTO_OUTPUT = TickTimeMonitor.create("auto_output");

    public static final TickTimeMonitor.Entry ACCELERATOR = TickTimeMonitor.create("accelerator");
    public static final TickTimeMonitor.Entry COLLECTION = TickTimeMonitor.create("collection");
    public static final TickTimeMonitor.Entry COMPUTATION = TickTimeMonitor.create("computation");
    public static final TickTimeMonitor.Entry PUMP = TickTimeMonitor.create("pump");
    public static final TickTimeMonitor.Entry BREAKER = TickTimeMonitor.create("breaker");
    public static final TickTimeMonitor.Entry FISHING = TickTimeMonitor.create("fishing");
    public static final TickTimeMonitor.Entry ENERGY_CONVERT = TickTimeMonitor.create("energy_convert");
    public static final TickTimeMonitor.Entry CABLE = TickTimeMonitor.create("cable");
    public static final TickTimeMonitor.Entry CHARGE = TickTimeMonitor.create("charge");
    public static final TickTimeMonitor.Entry ENERGY_TRANSFER = TickTimeMonitor.create("energy_transfer");
    public static final TickTimeMonitor.Entry STEAM = TickTimeMonitor.create("steam");

    public static void init() {
        AddonFinder.getAddons().forEach(IGTAddon::registerTickTimeMonitor);
        REGISTRY.freeze();
    }
}
