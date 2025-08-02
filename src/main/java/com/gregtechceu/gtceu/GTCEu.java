package com.gregtechceu.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.client.ClientProxy;
import com.gregtechceu.gtceu.common.CommonProxy;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(GTCEu.MOD_ID)
public class GTCEu {

    public static final String MOD_ID = "gtceu";
    public static final String NAME = "GregTechCEu";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public GTCEu() {
        GTCEu.init();
        GTCEuAPI.instance = this;
        DistExecutor.unsafeRunForDist(() -> ClientProxy::new, () -> CommonProxy::new);
    }

    public static void init() {
        LOGGER.info("{} is initializing...", NAME);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /**
     * @return if we're running in a production environment
     */
    public static boolean isProd() {
        return FMLLoader.isProduction();
    }

    /**
     * @return if we're not running in a production environment
     */
    public static boolean isDev() {
        return !isProd();
    }

    /**
     * @return if we're running data generation
     */
    public static boolean isDataGen() {
        return FMLLoader.getLaunchHandler().isData();
    }

    /**
     * A friendly reminder that the server instance is populated on the server side only, so null/side check it!
     * 
     * @return the current minecraft server instance
     */
    public static MinecraftServer getMinecraftServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /**
     * @param modId the mod id to check for
     * @return if the mod whose id is {@code modId} is loaded or not
     */
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * For async stuff use this, otherwise use {@link GTCEu isClientSide}
     * 
     * @return if the current thread is the client thread
     */
    public static boolean isClientThread() {
        return isClientSide() && Minecraft.getInstance().isSameThread();
    }

    /**
     * @return if the game is the <strong>PHYSICAL</strong> client, e.g. not a dedicated server.
     * @apiNote Do not use this to check if you're currently on the server thread for side-specific actions!
     *          It does <strong>NOT</strong> work for that. Use {@link #isClientThread()} instead.
     * @see #isClientThread()
     */
    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }

    /**
     * This check isn't the same for client and server!
     * 
     * @return if it's safe to access the current instance {@link net.minecraft.world.level.Level Level} on client or if
     *         it's safe to access any level on server.
     */
    public static boolean canGetServerLevel() {
        if (isClientSide()) {
            return Minecraft.getInstance().level != null;
        }
        var server = getMinecraftServer();
        return server != null &&
                !(server.isStopped() || server.isShutdown() || !server.isRunning() || server.isCurrentlySaving());
    }

    /**
     * @return the path to the minecraft instance directory
     */
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static class Mods {

        private static final boolean EMI_LOADED = isModLoaded(GTValues.MODID_EMI);
        private static final boolean AE2_LOADED = isModLoaded(GTValues.MODID_APPENG);
        private static final boolean CURIOS_LOADED = isModLoaded(GTValues.MODID_CURIOS);
        private static final boolean SHIMMER_LOADED = isModLoaded(GTValues.MODID_SHIMMER);
        private static final boolean FTB_TEAMS_LOADED = isModLoaded(GTValues.MODID_FTB_TEAMS);
        private static final boolean FTB_QUESTS_LOADED = isModLoaded(GTValues.MODID_FTB_QUEST);

        public static boolean isEMILoaded() {
            return EMI_LOADED;
        }

        public static boolean isAE2Loaded() {
            return AE2_LOADED;
        }

        public static boolean isCuriosLoaded() {
            return CURIOS_LOADED;
        }

        public static boolean isShimmerLoaded() {
            return SHIMMER_LOADED;
        }

        public static boolean isFTBTeamsLoaded() {
            return FTB_TEAMS_LOADED;
        }

        public static boolean isFTBQuestsLoaded() {
            return FTB_QUESTS_LOADED;
        }
    }
}
