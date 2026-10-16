package com.example.espoints;

import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.command.HCRCommand;
import com.example.espoints.config.ModConfig;
import com.example.espoints.config.TacticalMapConfig;
import com.example.espoints.config.TeamfightJsonConfig;
import com.example.espoints.integration.EspetroBattlefieldIntegration;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.SyncTacticalMapBackgroundMessage;
import com.example.espoints.network.RequestTacticalMapTileMessage;
import com.example.espoints.network.RequestRateLimiter;
import com.example.espoints.network.SyncTacticalMapConfigMessage;
import com.example.espoints.tactical.TacticalMarkerManager;
import com.example.espoints.tile.TacticalMapTileService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ESPointsMod.MOD_ID)
public class ESPointsMod {
    public static final String MOD_ID = "espoints";
    public static final Logger LOGGER = LogManager.getLogger();
    
    // 单例实例
    private static ESPointsMod INSTANCE;
    
    // 客户端专用的HUD实例 - 仅在客户端初始化
    @OnlyIn(Dist.CLIENT)
    private static com.example.espoints.hud.CapturePointHUD capturePointHUD;
    @OnlyIn(Dist.CLIENT)
    private static com.example.espoints.hud.AreaInfoHUD areaInfoHUD;
    @OnlyIn(Dist.CLIENT)
    private static com.example.espoints.hud.CurrentCapturePointHUD currentCapturePointHUD;
    @OnlyIn(Dist.CLIENT)
    private static com.example.espoints.hud.ReinforcementsHUD reinforcementsHUD;
    @OnlyIn(Dist.CLIENT)
    private static com.example.espoints.hud.MessagePopup messagePopup;

    public ESPointsMod(FMLJavaModLoadingContext context) {
        INSTANCE = this;
        IEventBus modEventBus = context.getModEventBus();
        
        // 注册配置
        context.registerConfig(Type.COMMON, ModConfig.SPEC);
        context.registerConfig(Type.CLIENT, TacticalMapConfig.SPEC);
        
        // 注册mod事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // 注册Forge事件
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CapturePointManager.getInstance());
        MinecraftForge.EVENT_BUS.register(new EspetroBattlefieldIntegration());
    }
    
    /**
     * 客户端专用的事件监听器类
     */
    @OnlyIn(Dist.CLIENT)
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    private static class ClientModEvents {
        @SubscribeEvent
        public static void registerOverlays(final RegisterGuiOverlaysEvent event) {
            // 初始化HUD实例（如果还没有初始化）
            if (capturePointHUD == null) {
                capturePointHUD = new com.example.espoints.hud.CapturePointHUD();
                areaInfoHUD = new com.example.espoints.hud.AreaInfoHUD();
                currentCapturePointHUD = new com.example.espoints.hud.CurrentCapturePointHUD();
                reinforcementsHUD = new com.example.espoints.hud.ReinforcementsHUD();
                messagePopup = com.example.espoints.hud.MessagePopup.getInstance();
            }
            
            // 注册HUD覆盖层
            event.registerBelowAll("capture_point_hud", capturePointHUD);
            event.registerBelowAll("area_info_hud", areaInfoHUD);
            event.registerBelowAll("current_capture_point_hud", currentCapturePointHUD);
            // V 键小地图不在这里注册 overlay：Forge 的 overlay 列表只能保证「排在其它 overlay 之后」，
            // 而 Espetro 等模组的 HUD 是在 RenderGuiEvent.Post（整个 overlay 循环之后）绘制的，
            // overlay 排得再靠后也盖不住它们。因此小地图改由
            // TacticalMapHUD#onRenderMinimapTopmost（RenderGuiEvent.Post + EventPriority.LOWEST）绘制；
            // 这里仍提前实例化，确保它在 Forge 事件总线上注册好渲染与滚轮回调。
            com.example.espoints.hud.TacticalMapHUD.getInstance();
            event.registerBelowAll("reinforcements_hud", reinforcementsHUD);
            event.registerBelowAll("message_popup", messagePopup);
        }
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 初始化网络处理器
        NetworkHandler.registerMessages();
        
        // 移除了游戏规则，改用命令系统控制地图显示玩家位置
    }
    
    private void clientSetup(final FMLClientSetupEvent event) {
        // 初始化客户端专用的HUD实例
        capturePointHUD = new com.example.espoints.hud.CapturePointHUD();
        areaInfoHUD = new com.example.espoints.hud.AreaInfoHUD();
        currentCapturePointHUD = new com.example.espoints.hud.CurrentCapturePointHUD();
        reinforcementsHUD = new com.example.espoints.hud.ReinforcementsHUD();
        messagePopup = com.example.espoints.hud.MessagePopup.getInstance();
        
        // 客户端初始化
        LOGGER.info("HCR Points Mod客户端初始化完成！");
        
        // 生成教程文件
        com.example.espoints.util.TutorialManager.generateTutorialFiles();
        
        // 注册配置界面
        registerConfigScreens();
    }
    
    /**
     * 注册配置界面
     */
    @OnlyIn(Dist.CLIENT)
    private void registerConfigScreens() {
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> {
                // 返回服务端配置界面
                return new com.example.espoints.client.gui.ServerConfigScreen(parent);
            })
        );
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 注册命令
        HCRCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        TacticalMarkerManager.reset();
        CapturePointManager.getInstance().resetTransientSyncCaches();
        TeamfightJsonConfig.clearFrozenSnapshot();
        TacticalMapTileService.get().clear();
        RequestTacticalMapTileMessage.clearAll();
        RequestRateLimiter.clearAll();
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            SyncTacticalMapConfigMessage.sendToPlayer(event.getPlayer());
            SyncTacticalMapBackgroundMessage.sendToPlayer(event.getPlayer());
        } else {
            SyncTacticalMapConfigMessage.broadcastToAll();
            SyncTacticalMapBackgroundMessage.broadcastToAll();
        }
    }
    
    /**
     * 获取服务器实例
     * @return Minecraft服务器实例
     */
    public static net.minecraft.server.MinecraftServer getServer() {
        // 通过ServerLifecycleHooks获取当前运行的服务器实例
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }
    
    /**
     * 检查服务器是否正在运行
     * @return 如果服务器正在运行返回true，否则返回false
     */
    public static boolean isServerRunning() {
        return getServer() != null;
    }
    
    /**
     * 检查是否正在客户端运行
     * @return 如果在客户端运行返回true，否则返回false
     */
    public static boolean isClientRunning() {
        return net.minecraft.client.Minecraft.getInstance() != null;
    }
}
