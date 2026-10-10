package com.example.espoints.hud;

import com.example.espoints.ESPointsMod;
import com.example.espoints.capturepoint.CapturePoint;
import com.example.espoints.client.ClientBattleState;
import com.example.espoints.client.ClientTacticalMapTileCache;
import com.example.espoints.client.ClientPlayerIdentityState;
import com.example.espoints.capturepoint.DisplayState;
import com.example.espoints.config.TacticalMapConfig;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.network.SyncBastionsMessage;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.PlaceTacticalMarkerMessage;
import com.example.espoints.network.RemoveTacticalMarkerMessage;
import com.example.espoints.network.RequestTacticalMarkersMessage;
import com.example.espoints.network.SelectArtillerySupportTargetMessage;
import com.example.espoints.network.TacticalMapSubscriptionMessage;
import com.example.espoints.tactical.TacticalMarker;
import com.example.espoints.tactical.TacticalMarkerType;
import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapLodPlanner;
import com.example.espoints.tile.TacticalMapLayerPicker;
import com.example.espoints.tile.TacticalMapTileScreenMath;
import com.example.espoints.tile.TacticalMapCircleMesh;
import com.example.espoints.tile.TacticalMapLabelLayout;
import com.example.espoints.tile.TacticalMapStaticProjection;
import com.example.espoints.tile.TacticalMapViewportQuantizer;
import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 战术地图HUD类，通过V键打开，显示玩家周围更大范围的据点信息
 */
public class TacticalMapHUD implements IGuiOverlay {
    private static final double ZOOM_FACTOR = 1.25D;
    private static final int MAP_TITLE_HEIGHT = 20;
    private static final int EMBEDDED_MAP_TITLE_HEIGHT = 14;
    private static final float EMBEDDED_TEXT_SCALE = 0.68F;
    private static final ResourceLocation VANILLA_MAP_ICONS =
        ResourceLocation.withDefaultNamespace("textures/map/map_icons.png");
    private static final ResourceLocation SQUAD_HAB_ICON =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/squad/hab.png");
    private static final ResourceLocation SQUAD_RADIO_ICON =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/squad/radio.png");
    private static final ResourceLocation SQUAD_RALLY_ICON =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/squad/rally.png");
    private static final ResourceLocation MAP_SOLDIER =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/soldier.png");
    private static final ResourceLocation MAP_EN_SOLDIER =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_soldier.png");
    private static final ResourceLocation MAP_TANK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/tank.png");
    private static final ResourceLocation MAP_HELI =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/heli.png");
    private static final ResourceLocation MAP_EN_HELI =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_heli.png");
    private static final ResourceLocation MAP_TRUCK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/truck.png");
    private static final ResourceLocation MAP_IFV =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/map_ifv.png");
    private static final ResourceLocation MAP_FOB =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/fob.png");
    private static final ResourceLocation MAP_EN_FOB =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_fob.png");
    private static final ResourceLocation MAP_RADIO =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/radio.png");
    private static final ResourceLocation MAP_HAB =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/hab.png");
    private static final ResourceLocation MAP_HAB_ACTIVATED =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/hab_activated.png");
    private static final ResourceLocation MAP_MAINSPAWN =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mainspawn.png");
    private static final ResourceLocation MAP_MARK_ATTACK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_attack.png");
    private static final ResourceLocation MAP_MARK_DEFEND =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_defend.png");
    private static final ResourceLocation MAP_RALLY =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/rally.png");
    private static final ResourceLocation MAP_EN_RALLY =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_rally.png");
    private static final ResourceLocation MAP_ACP =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/acp.png");
    private static final ResourceLocation MAP_EN_ACP =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_acp.png");
    private static final ResourceLocation MAP_VEHICLE_SUPPLY =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/vehicle_supply.png");
    private static final int COLOR_FRIENDLY_WHITE = 0xFFFFFFFF;
    private static final int COLOR_ENEMY_RED = 0xFFFF5555;
    private static final int MAP_BACKGROUND_FALLBACK_COLOR = 0xAA1D211B;
    // 所有地图标志使用最大显示范围时的固定像素尺寸，不再随缩放变化。
    private static final int LOCAL_PLAYER_MARKER_SIZE = 7;
    private static final int TEAMMATE_MARKER_SIZE = 6;
    private static final int CAPTURE_POINT_MARKER_SIZE = 3;
    private static final int BASTION_MARKER_SIZE = 12;
    private static final int VEHICLE_SUPPLY_STATION_MARKER_SIZE = 6;
    private static final int BASE_MARKER_SIZE = 7;
    // 与主基地/兵站同量级（blit 时 half = size/2，约 10–12 像素）。
    private static final int TACTICAL_MARKER_SIZE = 10;
    private static final int SAME_BATCH_ROUTE_COLOR = 0xCCFFD166;
    private static final int SELECTED_DEPLOYMENT_FRAME_SIZE = 15;
    private static final int SELECTED_DEPLOYMENT_FRAME_MAX_SIZE = 24;
    private static final long SELECTED_DEPLOYMENT_ANIMATION_MS = 180L;
    private static final double SELECTED_DEPLOYMENT_MATCH_DISTANCE_SQUARED = 8.0D * 8.0D;
    // 与服务端约 5tick(250ms) 同步对齐；略留缓冲避免早到终点后冻结。
    private static final long MIN_PLAYER_INTERPOLATION_MS = 50L;
    private static final long MAX_PLAYER_INTERPOLATION_MS = 500L;
    private static final long PLAYER_INTERPOLATION_BUFFER_MS = 50L;
    private static final long MAX_PLAYER_EXTRAPOLATION_MS = 250L;
    private static final double PLAYER_TELEPORT_DISTANCE_SQUARED = 256.0D * 256.0D;
    private static final long SUBSCRIPTION_HEARTBEAT_INTERVAL_MS = 4_000L;
    private double lastViewMinX;
    private double lastViewMinY = 0.0D;
    private double lastViewMaxX = 1.0D;
    private double lastViewMaxY = 1.0D;
    private int lastViewScreenWidth = 256;
    private int lastViewScreenHeight = 256;
    private static final int[] ROUTE_COLORS = {
            0xFFFF5555,
            0xFF55FF55,
            0xFF5599FF,
            0xFFFFFF55,
            0xFFFF55FF,
            0xFF55FFFF,
            0xFFFFAA55
    };
    
    private boolean isMapVisible = false; // 地图是否可见
    /** 小地图开关动画：0.2 秒，从屏幕左缘滑入 / 向左滑出。 */
    private static final long MAP_SLIDE_MS = 200L;
    private long mapSlideStartMs;
    private boolean mapSlideTargetVisible;
    private double visibleWorldSpan = -1.0D;
    /** 缩放目标跨度：visibleWorldSpan 每帧向它插值，做出丝滑的放大/缩小过渡。 */
    private double targetWorldSpan = -1.0D;
    /** 插值速度（每帧逼近目标的比例）。 */
    private static final double ZOOM_SMOOTHING = 0.25D;
    private List<CapturePoint> allPoints = List.of();
    private List<SyncBastionsMessage.BastionInfo> visibleBastions = List.of();
    private List<SyncBastionsMessage.BaseInfo> visibleBases = List.of();
    private List<SyncBastionsMessage.VehicleSupplyStationInfo> visibleVehicleSupplyStations = List.of();
    private List<TacticalMarker> visibleTacticalMarkers = List.of();
    private com.example.espoints.network.SyncFriendlyVehiclesMessage friendlyVehicleFrame;
    private long lastFriendlyVehicleFrame;
    public void syncFriendlyVehicles(com.example.espoints.network.SyncFriendlyVehiclesMessage message) {
        friendlyVehicleFrame = message; lastFriendlyVehicleFrame = System.currentTimeMillis();
    }
    private void renderFriendlyVehicles(GuiGraphics graphics, MapViewport viewport, String team) {
        var mc = Minecraft.getInstance();
        if (friendlyVehicleFrame == null || mc.level == null || mc.player == null
            || System.currentTimeMillis() - lastFriendlyVehicleFrame > 2500
            || !mc.level.dimension().location().toString().equals(friendlyVehicleFrame.dimension())
            || !EspetroTeamBridge.isSameTeam(team, friendlyVehicleFrame.team())) return;
        for (var vehicle : friendlyVehicleFrame.vehicles()) {
            if (!viewport.containsWorld(vehicle.x(), vehicle.z())) continue;
            double x = viewport.screenXd(vehicle.x()), y = viewport.screenYd(vehicle.z());
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(vehicle.yaw() + 180));
                // 载具图标缩到原来的 1/4（12 → 3）
                blitMapIcon(graphics, 0, 0, 3,
                    com.example.espoints.tactical.FriendlyVehicleIcons.texture(vehicle.type()),
                    vehicle.loaded() ? COLOR_FRIENDLY_WHITE : 0x99FFFFFF);
            } finally { graphics.pose().popPose(); }
            if (markerHoverEnabled && Math.hypot(markerHoverMouseX - x, markerHoverMouseY - y) < 5) {
                String details = vehicle.name() + " · " + (vehicle.loaded()
                    ? vehicle.passengers() == 0 ? "无人乘坐" : "乘员 " + vehicle.passengers() : "最后已知位置");
                if (vehicle.squadId() >= 0) details += " · 小队 " + vehicle.squadId();
                graphics.drawString(mc.font, details, (int)x + 10, (int)y - 5, COLOR_FRIENDLY_WHITE, true);
            }
        }
    }
    
    // 存储从服务端同步的玩家位置
    private final Map<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> syncedPlayerPositions = new HashMap<>();
    private final Map<UUID, SmoothedPlayerMarker> smoothedPlayerMarkers = new HashMap<>();
    private final Map<UUID, Integer> cachedPlayerColors = new HashMap<>();
    private long cachedPlayerIdentityRevision = Long.MIN_VALUE;
    
    // 摄影机晃动相关变量
    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private float smoothShakeX = 0.0f;
    private float smoothShakeY = 0.0f;
    private static final float SMOOTH_FACTOR = 0.1f; // 平滑因子，数值越小晃动越平滑
    private static final int SHAKE_INTENSITY = 3; // 晃动强度

    private boolean draggingMap = false;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartCenterX;
    private double dragStartCenterZ;
    private double draggedCenterX;
    private double draggedCenterZ;
    private boolean customMapCenter = false;

    private int lastEmbeddedMapLeft = Integer.MIN_VALUE;
    private int lastEmbeddedMapTop = Integer.MIN_VALUE;
    private int lastEmbeddedMapWidth;
    private int lastEmbeddedMapHeight;
    private Object lastEmbeddedMapScreen;
    private long lastEmbeddedMapRenderMs;
    /** 实际渲染内容矩形的纵横比（高/宽，已剔除顶部标题区），保证 scaleX==scaleZ 无变形。 */
    private double contentAspectK = -1.0D;
    private boolean compactEmbeddedRendering;
    private int lastRecenterButtonLeft = Integer.MIN_VALUE;
    private int lastRecenterButtonTop = Integer.MIN_VALUE;
    private int lastRecenterButtonWidth;
    private int lastRecenterButtonHeight;
    private MapViewport lastInteractiveViewport;
    private Object lastMarkerRequestScreen;
    private final com.example.espoints.client.TacticalMapMarkerWheel markerWheel = new com.example.espoints.client.TacticalMapMarkerWheel();
    private double pendingMarkerWorldX;
    private double pendingMarkerWorldZ;
    private boolean artillerySelectionMode;
    private boolean suppressMapDragUntilRelease;
    private HoveredMapMarker hoveredMapMarker;
    private double hoveredMapMarkerDistanceSquared = Double.MAX_VALUE;
    private double markerHoverMouseX;
    private double markerHoverMouseY;
    private boolean markerHoverEnabled;
    private boolean hasSelectedDeploymentPoint;
    private double selectedDeploymentX;
    private double selectedDeploymentZ;
    private long selectedDeploymentAnimationStartedAt;
    private long lastSubscriptionHeartbeatMs;
    private final TacticalMapLodPlanner mapLodPlanner = new TacticalMapLodPlanner();
    private final TacticalMapLabelLayout mapLabelLayout = new TacticalMapLabelLayout();
    private final TacticalMapStaticProjection staticProjection = new TacticalMapStaticProjection();
    private long staticMapRevision;
    private Object lastLabelFrameKey;
    private long lastLabelLayoutMs;
    
    public TacticalMapHUD() {
        // 注册事件监听器
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void syncVisibleCapturePointsFromServer(List<CapturePoint> syncedPoints) {
        List<CapturePoint> next = List.copyOf(syncedPoints);
        if (!this.allPoints.equals(next)) {
            this.allPoints = next;
            staticMapRevision++;
        }
    }

    public void syncBastionsFromServer(List<SyncBastionsMessage.BastionInfo> bastions) {
        this.visibleBastions = List.copyOf(bastions);
        this.visibleBases = List.of();
        this.visibleVehicleSupplyStations = List.of();
        staticMapRevision++;
    }

    public void syncBastionsFromServer(List<SyncBastionsMessage.BastionInfo> bastions,
                                       List<SyncBastionsMessage.BaseInfo> bases) {
        syncBastionsFromServer(bastions, bases, List.of());
    }

    public void syncBastionsFromServer(List<SyncBastionsMessage.BastionInfo> bastions,
                                       List<SyncBastionsMessage.BaseInfo> bases,
                                       List<SyncBastionsMessage.VehicleSupplyStationInfo> vehicleSupplyStations) {
        this.visibleBastions = List.copyOf(bastions);
        this.visibleBases = List.copyOf(bases);
        this.visibleVehicleSupplyStations = vehicleSupplyStations == null
            ? List.of()
            : List.copyOf(vehicleSupplyStations);
        staticMapRevision++;
    }

    public void syncTacticalMarkersFromServer(List<TacticalMarker> markers) {
        this.visibleTacticalMarkers = markers == null ? List.of() : List.copyOf(markers);
        staticMapRevision++;
    }
    
    /**
     * 切换地图显示/隐藏状态
     */
    public void toggleMapVisibility() {
        isMapVisible = !isMapVisible;
        mapSlideStartMs = System.currentTimeMillis();
        mapSlideTargetVisible = isMapVisible;
        draggingMap = false;

        Minecraft mc = Minecraft.getInstance();
        if (isMapVisible) {
            TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
            resetVisibleSpan(config);
            customMapCenter = false;
            ensureTacticalMapSubscription();
        } else {
            ClientTacticalMapTileCache.get().suspendRequests();
            sendTacticalMapSubscription(false);
        }
    }
    
    /**
     * 切换地图显示模式
     */
    public void cycleDisplayMode() {
        // 地图现在只有一种显示形态，保留方法避免旧按键调用报错。
    }
    
    /**
     * 获取当前显示模式
     */
    public MapDisplayMode getDisplayMode() {
        return MapDisplayMode.TOGGLE_KEY;
    }
    
    /**
     * 获取地图是否可见（按键唤出模式下）
     */
    public boolean isMapVisible() {
        return isMapVisible;
    }

    public void increaseRenderRange() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = getCurrentBounds(config);
        ensureVisibleSpan(config, bounds);
        // 缩小（看全图）最多到「整图可见」跨度，不再受 bounds.size() 限制。
        targetWorldSpan = Math.min(wholeMapSpan(bounds), currentTargetSpan() * ZOOM_FACTOR);
        preserveCustomViewportCenter(bounds);
    }

    public void decreaseRenderRange() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = getCurrentBounds(config);
        ensureVisibleSpan(config, bounds);
        targetWorldSpan = Math.max(config.getMinimumRange(bounds), currentTargetSpan() / ZOOM_FACTOR);
        preserveCustomViewportCenter(bounds);
    }

    /** 当前缩放目标（未经插值的期望跨度）。 */
    private double currentTargetSpan() {
        return targetWorldSpan > 0.0D && !Double.isNaN(targetWorldSpan) ? targetWorldSpan : visibleWorldSpan;
    }

    /** 每帧把 visibleWorldSpan 向目标逼近，形成放大/缩小的平滑过渡。 */
    private void stepZoomAnimation() {
        if (targetWorldSpan <= 0.0D || Double.isNaN(targetWorldSpan)) { targetWorldSpan = visibleWorldSpan; return; }
        double diff = targetWorldSpan - visibleWorldSpan;
        if (Math.abs(diff) <= Math.max(0.5D, targetWorldSpan * 0.0015D)) { visibleWorldSpan = targetWorldSpan; return; }
        visibleWorldSpan += diff * ZOOM_SMOOTHING;
    }

    public void zoomFromMouseWheel(double delta) {
        if (delta > 0.0D) {
            decreaseRenderRange();
        } else if (delta < 0.0D) {
            increaseRenderRange();
        }
    }

    public void recenterOnPlayer() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = getCurrentBounds(config);
        ensureVisibleSpan(config, bounds);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            double[] clamped = clampViewportCenter(player.getX(), player.getZ(), visibleWorldSpan, bounds);
            draggedCenterX = clamped[0];
            draggedCenterZ = clamped[1];
        }
        draggingMap = false;
        customMapCenter = false;
    }

    public void onTacticalMapConfigSynced() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        resetVisibleSpan(config);
        draggingMap = false;
        customMapCenter = false;
    }

    public void clearServerSyncedBackgroundState() {
        markerWheel.close();
        friendlyVehicleFrame = null;
        lastFriendlyVehicleFrame = 0L;
        isMapVisible = false;
        mapSlideStartMs = 0L;
        mapSlideTargetVisible = false;
        visibleWorldSpan = -1.0D;
        targetWorldSpan = -1.0D;
        draggingMap = false;
        customMapCenter = false;
        lastSubscriptionHeartbeatMs = 0L;
        syncedPlayerPositions.clear();
        smoothedPlayerMarkers.clear();
        cachedPlayerColors.clear();
        cachedPlayerIdentityRevision = Long.MIN_VALUE;
        visibleBastions = List.of();
        visibleBases = List.of();
        visibleVehicleSupplyStations = List.of();
        visibleTacticalMarkers = List.of();
        allPoints = List.of();
        ClientTacticalMapTileCache.get().clear();
        mapLodPlanner.reset();
        mapLabelLayout.reset();
        staticMapRevision++;
    }

    private void ensureTacticalMapSubscription() {
        long now = System.currentTimeMillis();
        if (lastSubscriptionHeartbeatMs > 0L
                && now - lastSubscriptionHeartbeatMs < SUBSCRIPTION_HEARTBEAT_INTERVAL_MS) {
            return;
        }
        sendTacticalMapSubscription(true);
    }

    private void sendTacticalMapSubscription(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) {
            if (!active) {
                lastSubscriptionHeartbeatMs = 0L;
            }
            return;
        }
        if (active) {
            NetworkHandler.INSTANCE.sendToServer(new TacticalMapSubscriptionMessage(
                true, lastViewMinX, lastViewMinY, lastViewMaxX, lastViewMaxY,
                lastViewScreenWidth, lastViewScreenHeight));
        } else {
            NetworkHandler.INSTANCE.sendToServer(new TacticalMapSubscriptionMessage(false));
        }
        lastSubscriptionHeartbeatMs = active ? System.currentTimeMillis() : 0L;
    }

    /** 由 Espetro 部署界面通过反射设置当前选中的部署点。 */
    public void setSelectedDeploymentPoint(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            clearSelectedDeploymentPoint();
            return;
        }
        if (!hasSelectedDeploymentPoint
                || Math.abs(selectedDeploymentX - x) > 0.01D
                || Math.abs(selectedDeploymentZ - z) > 0.01D) {
            selectedDeploymentAnimationStartedAt = System.currentTimeMillis();
        }
        selectedDeploymentX = x;
        selectedDeploymentZ = z;
        hasSelectedDeploymentPoint = true;
    }

    public void clearSelectedDeploymentPoint() {
        hasSelectedDeploymentPoint = false;
    }
    
    /**
     * 渲染HUD界面
     * @param gui Forge GUI对象
     * @param guiGraphics GUI图形对象
     * @param partialTick 部分刻度
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    /** 小地图滑动偏移：0=就位，-(mapLeft+width+12)= 完全滑出到屏幕左缘之外。 */
    private double mapSlideOffset(int mapLeft, int mapWidth) {
        double full = mapLeft + mapWidth + 12.0D;
        if (mapSlideStartMs <= 0L) return isMapVisible ? 0.0D : -full;
        double t = Math.min(1.0D, (System.currentTimeMillis() - mapSlideStartMs) / (double) MAP_SLIDE_MS);
        double u = 1.0D - t;
        double e = 1.0D - u * u * u;   // easeOutCubic
        return mapSlideTargetVisible ? -full * (1.0D - e) : -full * e;
    }

    /** 隐藏且动画已结束 —— 不再绘制。 */
    private boolean mapFullyHidden() {
        return !isMapVisible
            && (mapSlideStartMs <= 0L || System.currentTimeMillis() - mapSlideStartMs >= MAP_SLIDE_MS);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (mapFullyHidden()) {
            return;
        }
        stepZoomAnimation();
        ensureTacticalMapSubscription();
        
        int margin = 8;
        int mapWidth = Mth.clamp((int)(screenWidth * 0.32), 220, 420);
        int mapHeight = Mth.clamp((int)(screenHeight * 0.34), 160, 300);
        int mapLeft = screenWidth - mapWidth - margin;
        int mapTop = margin;
        // 无标题/底部操作提示；缩放改由滚轮控制（见 onHudMouseScrolled）
        int slideX = (int) Math.round(mapSlideOffset(mapLeft, mapWidth));
        renderMapArea(guiGraphics, mapLeft + slideX, mapTop, mapWidth, mapHeight,
            "", false, false, partialTick);
    }

    public void renderEmbeddedMap(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, float partialTick) {
        stepZoomAnimation();
        ensureTacticalMapSubscription();
        Minecraft minecraft = Minecraft.getInstance();
        Object currentScreen = minecraft.screen;
        if (currentScreen != lastEmbeddedMapScreen) {
            markerWheel.close();
            lastInteractiveViewport = null;
        }
        if (currentScreen != null && currentScreen != lastMarkerRequestScreen
                && minecraft.getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMarkersMessage());
            lastMarkerRequestScreen = currentScreen;
        }
        lastEmbeddedMapLeft = mapLeft;
        lastEmbeddedMapTop = mapTop;
        lastEmbeddedMapWidth = mapWidth;
        lastEmbeddedMapHeight = mapHeight;
        lastEmbeddedMapScreen = currentScreen;
        lastEmbeddedMapRenderMs = System.currentTimeMillis();
        compactEmbeddedRendering = true;
        try {
            renderMapArea(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
                "战术地图 " + getRangeText() + " 鼠标滚轮缩放", true, partialTick);
        } finally {
            compactEmbeddedRendering = false;
        }
    }

    public void beginArtillerySelection() {
        artillerySelectionMode = true; markerWheel.close();
        suppressMapDragUntilRelease = false;
    }

    public void endArtillerySelection() {
        artillerySelectionMode = false; markerWheel.close();
        suppressMapDragUntilRelease = false;
        draggingMap = false;
        lastInteractiveViewport = null;
        clearRecenterButton();
    }

    public void renderArtillerySelectionMap(GuiGraphics guiGraphics, int mapLeft, int mapTop,
                                            int mapWidth, int mapHeight, float partialTick) {
        ensureTacticalMapSubscription();
        Minecraft minecraft = Minecraft.getInstance();
        Object currentScreen = minecraft.screen;
        if (currentScreen != lastEmbeddedMapScreen) {
            markerWheel.close();
            lastInteractiveViewport = null;
        }
        if (currentScreen != null && currentScreen != lastMarkerRequestScreen
                && minecraft.getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMarkersMessage());
            lastMarkerRequestScreen = currentScreen;
        }
        lastEmbeddedMapLeft = mapLeft;
        lastEmbeddedMapTop = mapTop;
        lastEmbeddedMapWidth = mapWidth;
        lastEmbeddedMapHeight = mapHeight;
        lastEmbeddedMapScreen = currentScreen;
        lastEmbeddedMapRenderMs = System.currentTimeMillis();
        renderMapArea(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
            "", true, false, partialTick);
    }

    public boolean zoomArtillerySelectionMap(double mouseX, double mouseY, double scrollDelta) {
        if (!artillerySelectionMode || !isInsideLastEmbeddedMap(mouseX, mouseY)) {
            return false;
        }
        zoomFromMouseWheel(scrollDelta);
        return true;
    }

    public boolean submitArtillerySelectionTarget(double mouseX, double mouseY) {
        if (!artillerySelectionMode
                || !isInsideLastEmbeddedMap(mouseX, mouseY)
                || lastInteractiveViewport == null
                || !lastInteractiveViewport.containsScreen(mouseX, mouseY)) {
            return false;
        }

        double worldX = lastInteractiveViewport.worldX(mouseX);
        double worldZ = lastInteractiveViewport.worldZ(mouseY);
        NetworkHandler.INSTANCE.sendToServer(new SelectArtillerySupportTargetMessage(worldX, worldZ));
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    /**
     * V 键右侧小地图打开且无界面时：滚轮缩放地图，并吞掉事件以免切换快捷栏。
     * 关闭地图后不处理，滚轮恢复正常。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHudMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        if (!isMapVisible) {
            return;
        }
        zoomFromMouseWheel(event.getScrollDelta());
        event.setCanceled(true);
    }

    /** 部署面板等内嵌战术地图：指针在地图内时滚轮缩放。 */
    @SubscribeEvent
    public void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (markerWheel.active()) { event.setCanceled(true); return; }
        if (isInsideLastEmbeddedMap(event.getMouseX(), event.getMouseY())) {
            zoomFromMouseWheel(event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (markerWheel.mouse(event.getButton(), true)) { event.setCanceled(true); return; }
        boolean insideEmbeddedMap = isInsideLastEmbeddedMap(event.getMouseX(), event.getMouseY());
        if (!insideEmbeddedMap) return;

        if (artillerySelectionMode && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (insideEmbeddedMap
                    && lastInteractiveViewport != null
                    && lastInteractiveViewport.containsScreen(event.getMouseX(), event.getMouseY())) {
                double worldX = lastInteractiveViewport.worldX(event.getMouseX());
                double worldZ = lastInteractiveViewport.worldZ(event.getMouseY());
                NetworkHandler.INSTANCE.sendToServer(new SelectArtillerySupportTargetMessage(worldX, worldZ));
                Minecraft.getInstance().setScreen(null);
            }
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isInsideLastRecenterButton(event.getMouseX(), event.getMouseY())) {
            recenterOnPlayer();
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && !artillerySelectionMode
                && lastInteractiveViewport != null
                && lastInteractiveViewport.containsScreen(event.getMouseX(), event.getMouseY())) {
            TacticalMarker clickedMarker = findTacticalMarkerAt(event.getMouseX(), event.getMouseY());
            if (clickedMarker != null) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null && player.getUUID().equals(clickedMarker.ownerId())) {
                    NetworkHandler.INSTANCE.sendToServer(
                        new RemoveTacticalMarkerMessage(clickedMarker.id()));
                } else if (player != null) {
                    player.displayClientMessage(Component.literal("§c只能取消自己放置的战术标点。"), true);
                }
                suppressMapDragUntilRelease = true;
                draggingMap = false;
                event.setCanceled(true);
                return;
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !artillerySelectionMode
                && lastInteractiveViewport != null
                && lastInteractiveViewport.containsScreen(event.getMouseX(), event.getMouseY())) {
            pendingMarkerWorldX = lastInteractiveViewport.worldX(event.getMouseX());
            pendingMarkerWorldZ = lastInteractiveViewport.worldZ(event.getMouseY());
            openMarkerMenu();
            event.setCanceled(true);
        }
    }

    private void renderMapArea(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                               String title, boolean allowMouseDrag, float partialTick) {
        renderMapArea(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
            title, allowMouseDrag, true, partialTick);
    }

    private void renderMapArea(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                               String title, boolean allowMouseDrag, boolean showInteractionChrome,
                               float partialTick) {
        if (mapWidth <= 0 || mapHeight <= 0) {
            return;
        }

        // 渲染地图背景（半透明）
        int bgColor = 0xCC202020;
        guiGraphics.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight, bgColor);
        
        // 渲染地图边框
        int borderColor = 0xFF000000;
        guiGraphics.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + 1, borderColor);
        guiGraphics.fill(mapLeft, mapTop, mapLeft + 1, mapTop + mapHeight, borderColor);
        guiGraphics.fill(mapLeft + mapWidth - 1, mapTop, mapLeft + mapWidth, mapTop + mapHeight, borderColor);
        guiGraphics.fill(mapLeft, mapTop + mapHeight - 1, mapLeft + mapWidth, mapTop + mapHeight, borderColor);
        
        if (showInteractionChrome && title != null && !title.isBlank()) {
            drawMapText(guiGraphics, title, mapLeft + 4,
                mapTop + (compactEmbeddedRendering ? 3 : 5), 0xFFFFFF);
        }

        if (allowMouseDrag && showInteractionChrome) {
            renderRecenterButton(guiGraphics, mapLeft, mapTop, mapWidth);
        } else {
            clearRecenterButton();
        }
        
        // 渲染鸟瞰图，包含玩家位置
        renderBirdsEyeView(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
            allowMouseDrag, showInteractionChrome, partialTick);
    }

    @SubscribeEvent
    public void tickMapWheel(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) markerWheel.tick();
    }
    @SubscribeEvent
    public void renderMapWheel(ScreenEvent.Render.Post event) { markerWheel.render(event.getGuiGraphics()); }
    @SubscribeEvent
    public void releaseMapWheel(ScreenEvent.MouseButtonReleased.Pre event) {
        if (markerWheel.mouse(event.getButton(), false)) event.setCanceled(true);
    }
    @SubscribeEvent
    public void keyMapWheel(ScreenEvent.KeyPressed.Pre event) {
        if (markerWheel.key(event.getKeyCode())) event.setCanceled(true);
    }
    private void openMarkerMenu() {
        draggingMap = false; suppressMapDragUntilRelease = true;
        markerWheel.open(pendingMarkerWorldX, pendingMarkerWorldZ);
    }

    private TacticalMarker findTacticalMarkerAt(double mouseX, double mouseY) {
        if (lastInteractiveViewport == null) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        String localTeam = EspetroTeamBridge.getPlayerTeam(player);
        TacticalMarker nearest = null;
        double nearestDistanceSquared = 7.0D * 7.0D;
        for (TacticalMarker marker : visibleTacticalMarkers) {
            if (!EspetroTeamBridge.isSameTeam(localTeam, marker.team())
                    || getTacticalMarkerOpacity(marker) <= 0.0F
                    || !lastInteractiveViewport.containsWorld(marker.x(), marker.z())) {
                continue;
            }
            double dx = mouseX - lastInteractiveViewport.screenXd(marker.x());
            double dy = mouseY - lastInteractiveViewport.screenYd(marker.z());
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = marker;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private void renderRecenterButton(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth) {
        String label = "归中";
        float textScale = getMapTextScale();
        int labelWidth = Math.round(Minecraft.getInstance().font.width(label) * textScale);
        int buttonWidth = compactEmbeddedRendering
            ? Math.max(25, labelWidth + 7)
            : Math.max(34, labelWidth + 12);
        int buttonHeight = compactEmbeddedRendering ? 10 : 14;
        int buttonLeft = mapLeft + mapWidth - buttonWidth - (compactEmbeddedRendering ? 4 : 6);
        int buttonTop = mapTop + (compactEmbeddedRendering ? 2 : 4);

        lastRecenterButtonLeft = buttonLeft;
        lastRecenterButtonTop = buttonTop;
        lastRecenterButtonWidth = buttonWidth;
        lastRecenterButtonHeight = buttonHeight;

        guiGraphics.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, 0xAA101010);
        guiGraphics.renderOutline(buttonLeft, buttonTop, buttonWidth, buttonHeight, 0xAAE6E06A);
        int textHeight = Math.max(1,
            Math.round(Minecraft.getInstance().font.lineHeight * textScale));
        drawMapText(guiGraphics, label,
            buttonLeft + Math.max(0, (buttonWidth - labelWidth) / 2),
            buttonTop + Math.max(0, (buttonHeight - textHeight) / 2),
            0xFFFFFF55);
    }

    private void clearRecenterButton() {
        lastRecenterButtonLeft = Integer.MIN_VALUE;
        lastRecenterButtonTop = Integer.MIN_VALUE;
        lastRecenterButtonWidth = 0;
        lastRecenterButtonHeight = 0;
    }
    
    /**
     * 渲染行动模式兵力显示
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param isMiniMap 是否是迷你地图
     */
    private void renderReinforcementsDisplay(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, boolean isMiniMap) {
        ClientBattleState manager = ClientBattleState.get();
        
        // 获取攻防双方队伍名称
        String attackerTeam = manager.attackerTeam();
        String defenderTeam = manager.defenderTeam();
        
        if (attackerTeam == null || defenderTeam == null) {
            return; // 队伍未设置完整，不显示
        }
        
        // 获取双方兵力
        int attackerReinforcements = manager.reinforcements(attackerTeam);
        int defenderReinforcements = manager.reinforcements(defenderTeam);
        
        // 计算进度条位置和尺寸
        int barHeight = 10;
        int barTop = isMiniMap ? mapTop + 5 : mapTop + 20; // 迷你地图上显示在顶部，完整地图显示在标题下方
        int barLeft = mapLeft + 5;
        int barRight = mapLeft + mapWidth - 5;
        int barWidth = barRight - barLeft;
        int halfBarWidth = barWidth / 2;
        
        // 从配置中获取颜色代码
        String defenderHexColor = com.example.espoints.config.TacticalMapConfig.defenderProgressBarColor.get();
        String attackerHexColor = com.example.espoints.config.TacticalMapConfig.attackerProgressBarColor.get();
        
        // 将颜色代码转换为整数颜色值
        int defenderColor = com.example.espoints.util.ModLogger.hexToColor(defenderHexColor, 0xFF0055FF); // 默认蓝色
        int attackerColor = com.example.espoints.util.ModLogger.hexToColor(attackerHexColor, 0xFFFF5500); // 默认红色
        
        // 渲染守方进度条
        int defenderInitialReinforcements = manager.initialReinforcements(defenderTeam);
        int defenderBarWidth = 0;
        if (defenderInitialReinforcements > 0) {
            defenderBarWidth = (int)((double)defenderReinforcements / defenderInitialReinforcements * halfBarWidth);
        }
        guiGraphics.fill(barLeft, barTop, barLeft + halfBarWidth, barTop + barHeight, 0x44000000); // 背景
        guiGraphics.fill(barLeft, barTop, barLeft + defenderBarWidth, barTop + barHeight, defenderColor); // 进度条
        
        // 渲染攻方进度条
        int attackerInitialReinforcements = manager.initialReinforcements(attackerTeam);
        int attackerBarWidth = 0;
        if (attackerInitialReinforcements > 0) {
            attackerBarWidth = (int)((double)attackerReinforcements / attackerInitialReinforcements * halfBarWidth);
        }
        int attackerBarLeft = barLeft + halfBarWidth;
        guiGraphics.fill(attackerBarLeft, barTop, attackerBarLeft + halfBarWidth, barTop + barHeight, 0x44000000); // 背景
        guiGraphics.fill(attackerBarLeft + halfBarWidth - attackerBarWidth, barTop, attackerBarLeft + halfBarWidth, barTop + barHeight, attackerColor); // 进度条
        
        // 绘制队伍名称和兵力数量
        Minecraft minecraft = Minecraft.getInstance();
        
        // 计算文字位置：迷你地图上显示在进度条上方，完整地图上显示在下方
        int textY = isMiniMap ? barTop - 10 : barTop + barHeight + 2;
        
        // 守方信息（左对齐）
        String defenderText = defenderTeam + ": " + defenderReinforcements;
        guiGraphics.drawString(
            minecraft.font,
            defenderText,
            barLeft,
            textY,
            0xFFFFFF,
            false
        );
        
        // 攻方信息（右对齐）
        String attackerText = attackerTeam + ": " + attackerReinforcements;
        guiGraphics.drawString(
            minecraft.font,
            attackerText,
            barRight - minecraft.font.width(attackerText),
            textY,
            0xFFFFFF,
            false
        );
    }
    
    /**
     * 从服务端同步玩家位置
     * @param playerPositions 玩家位置映射
     */
    public void syncPlayerPositionsFromServer(Map<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> playerPositions) {
        this.syncedPlayerPositions.keySet().retainAll(playerPositions.keySet());
        this.smoothedPlayerMarkers.keySet().retainAll(playerPositions.keySet());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> entry
                : playerPositions.entrySet()) {
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition position = entry.getValue();
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition mergedPosition =
                position;
            this.syncedPlayerPositions.put(entry.getKey(), mergedPosition);
            this.smoothedPlayerMarkers.compute(entry.getKey(), (uuid, marker) -> {
                if (marker == null) {
                    return new SmoothedPlayerMarker(mergedPosition, now);
                }
                marker.updateTarget(mergedPosition, now);
                return marker;
            });
        }
    }
    
    /**
     * 在地图上渲染其他玩家位置
     * @param guiGraphics GUI图形对象
     * @param localPlayer 本地玩家
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     */
    private void renderOtherPlayersOnMap(GuiGraphics guiGraphics, LocalPlayer localPlayer, MapViewport viewport,
                                         float partialTick) {
        // 使用配置类检查是否显示玩家位置
        boolean showPlayerLocations = com.example.espoints.config.MapPlayerDisplayConfig.getInstance().isShowPlayerLocations();
        
        if (!showPlayerLocations) {
            // 配置不允许显示，不渲染其他玩家位置
            return;
        }

        String localTeam = EspetroTeamBridge.getPlayerTeam(localPlayer);
        long renderTime = System.currentTimeMillis();
        refreshPlayerIdentityCache();
        
        // 遍历从服务端同步的玩家位置
        for (Map.Entry<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> entry : syncedPlayerPositions.entrySet()) {
            UUID playerUUID = entry.getKey();
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition pos = entry.getValue();
            
            // 跳过本地玩家
            if (playerUUID.equals(localPlayer.getUUID())) {
                continue;
            }
            
            // 获取其他玩家坐标
            SmoothedPlayerMarker smoothedMarker = smoothedPlayerMarkers.get(playerUUID);
            if (smoothedMarker != null) {
                smoothedMarker.sample(renderTime);
            }
            double otherPlayerX = smoothedMarker == null ? pos.getX() : smoothedMarker.renderX;
            double otherPlayerZ = smoothedMarker == null ? pos.getZ() : smoothedMarker.renderZ;
            float otherPlayerYaw = smoothedMarker == null ? pos.getYaw() : smoothedMarker.renderYaw;

            if (!viewport.containsWorld(otherPlayerX, otherPlayerZ)) {
                continue;
            }
            
            if (!EspetroTeamBridge.isSameTeam(localTeam, pos.getTeamName())) {
                continue;
            }

            double mapPosX = viewport.screenXd(otherPlayerX);
            double mapPosY = viewport.screenYd(otherPlayerZ);
            // 指挥官金 / 本队队长紫 / 本队蓝 / 同阵营其它白
            int teammateColor = cachedPlayerColors.computeIfAbsent(playerUUID, ignored ->
                EspetroTeamBridge.getMapPlayerColor(
                    pos.getName(), pos.getSquadId(),
                    pos.isSquadLeader(), pos.isCommander()));
            renderMapPlayerIcon(guiGraphics, mapPosX, mapPosY, otherPlayerYaw,
                TEAMMATE_MARKER_SIZE, teammateColor);
            considerMarkerHover(mapPosX, mapPosY, TEAMMATE_MARKER_SIZE, otherPlayerX, otherPlayerZ);
        }
        
        // 如果没有同步到玩家位置，尝试直接获取本地玩家列表作为备选方案
        if (syncedPlayerPositions.isEmpty()) {
            // 使用配置类检查是否显示玩家位置
            showPlayerLocations = com.example.espoints.config.MapPlayerDisplayConfig.getInstance().isShowPlayerLocations();
            if (!showPlayerLocations) {
                // 配置不允许显示，不渲染其他玩家位置
                return;
            }
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                for (net.minecraft.world.entity.player.Player otherPlayer : minecraft.level.players()) {
                    if (otherPlayer == localPlayer) {
                        continue;
                    }
                    if (!EspetroTeamBridge.isPlayerVisibleOnTacticalMap(otherPlayer)) {
                        continue;
                    }

                    Entity otherBody = getMapRenderBody(otherPlayer);
                    double otherPlayerX = Mth.lerp(partialTick, otherBody.xo, otherBody.getX());
                    double otherPlayerZ = Mth.lerp(partialTick, otherBody.zo, otherBody.getZ());
                    if (!viewport.containsWorld(otherPlayerX, otherPlayerZ)) {
                        continue;
                    }

                    if (!EspetroTeamBridge.isSameTeam(localTeam, EspetroTeamBridge.getPlayerTeam(otherPlayer))) {
                        continue;
                    }

                    double mapPosX = viewport.screenXd(otherPlayerX);
                    double mapPosY = viewport.screenYd(otherPlayerZ);
                    int teammateColor = EspetroTeamBridge.getMapPlayerColor(otherPlayer.getName().getString());
                    renderMapPlayerIcon(guiGraphics, mapPosX, mapPosY,
                        Mth.rotLerp(partialTick, otherPlayer.yRotO, otherPlayer.getYRot()),
                        TEAMMATE_MARKER_SIZE, teammateColor);
                    considerMarkerHover(mapPosX, mapPosY, TEAMMATE_MARKER_SIZE,
                        otherPlayerX, otherPlayerZ);
                }
            }
        }
    }

    private void refreshPlayerIdentityCache() {
        long revision = ClientPlayerIdentityState.get().revision();
        if (revision != cachedPlayerIdentityRevision) {
            cachedPlayerColors.clear();
            cachedPlayerIdentityRevision = revision;
        }
    }
    
    /**
     * 渲染鸟瞰图
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param alpha 透明度
     */
    private void renderBirdsEyeView(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                                    boolean allowMouseDrag, boolean showInteractionChrome, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        ensureVisibleSpan(config, bounds);

        // 乘车时用根载具插值坐标，避免乘客每 tick 贴座位导致 prevPos 不可靠而抽动。
        Entity localBody = getMapRenderBody(player);
        double playerRenderX = Mth.lerp(partialTick, localBody.xo, localBody.getX());
        double playerRenderZ = Mth.lerp(partialTick, localBody.zo, localBody.getZ());
        float playerRenderYaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());

        MapViewport content = createViewport(config, bounds, playerRenderX, playerRenderZ,
            mapLeft, mapTop, mapWidth, mapHeight, getMapTitleHeight(showInteractionChrome));
        if (allowMouseDrag) {
            handleMapDrag(content, bounds);
            content = createViewport(config, bounds, playerRenderX, playerRenderZ,
                mapLeft, mapTop, mapWidth, mapHeight, getMapTitleHeight(showInteractionChrome));
            lastInteractiveViewport = content;
        } else {
            draggingMap = false;
            customMapCenter = false;
            lastInteractiveViewport = null;
        }

        beginMarkerHover(allowMouseDrag && showInteractionChrome, content);

        Object overlayKey = labelFrameKey(content, config);
        mapLabelLayout.begin(overlayKey,
            new TacticalMapLabelLayout.Bounds(
                content.left, content.top, content.right(), content.bottom()));
        staticProjection.begin(overlayKey);

        guiGraphics.enableScissor(content.left, content.top, content.right(), content.bottom());
        renderMapBackground(guiGraphics, content);
        var grid = new com.example.espoints.tactical.TacticalMapGrid(bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ);
        var gridView = new TacticalMapGridRenderer.View(content.left, content.top, content.width, content.height,
            content.viewMinX, content.viewMinZ, content.scaleX, content.scaleZ);
        var gridHover = markerHoverEnabled
            ? grid.address(gridView.worldX(markerHoverMouseX), gridView.worldZ(markerHoverMouseY)).orElse(null) : null;
        if (config.showGrid) TacticalMapGridRenderer.drawGrid(guiGraphics, grid, gridView, gridHover);

        String localTeam = EspetroTeamBridge.getPlayerTeam(player);

        // 图标旁名称会挡住底图，产品要求不再绘制。
        renderBasesOnMap(guiGraphics, content, player, false, localTeam);

        renderFobRadiiOnMap(guiGraphics, content, localTeam);

        renderCapturePointsOnMap(guiGraphics, content, player, true);

        // 渲染己方兵站
        renderBastionsOnMap(guiGraphics, content, player, false, localTeam);

        renderVehicleSupplyStationsOnMap(guiGraphics, content, false, localTeam);
        renderFriendlyVehicles(guiGraphics, content, localTeam);

        renderTacticalMarkersOnMap(guiGraphics, content, false, localTeam);

        // 渲染其他玩家位置
        renderOtherPlayersOnMap(guiGraphics, player, content, partialTick);

        // 玩家在载具上时由载具图标代表，不再单独画玩家图标
        if (player.getVehicle() == null
                && EspetroTeamBridge.isPlayerVisibleOnTacticalMap(player)
                && content.containsWorld(playerRenderX, playerRenderZ)) {
            renderMapPlayerIcon(guiGraphics, content.screenXd(playerRenderX), content.screenYd(playerRenderZ),
                playerRenderYaw, LOCAL_PLAYER_MARKER_SIZE,
                COLOR_FRIENDLY_WHITE);
            considerMarkerHover(content.screenXd(playerRenderX), content.screenYd(playerRenderZ),
                LOCAL_PLAYER_MARKER_SIZE, playerRenderX, playerRenderZ);
        }

        renderSelectedDeploymentFrame(guiGraphics, content);

        // 右键菜单（标点轮盘）打开时不画坐标刻度：它们会盖住菜单，且此时也不需要坐标。
        if (config.showGrid && !markerWheel.active()) {
            TacticalMapGridRenderer.drawChrome(guiGraphics, grid, gridView, gridHover, markerHoverMouseX, markerHoverMouseY,
                hoveredMapMarker == null ? null : "标记 X: " + hoveredMapMarker.x + "  Z: " + hoveredMapMarker.z,
                showInteractionChrome);
        }
        guiGraphics.disableScissor();
        guiGraphics.renderOutline(content.left, content.top, content.width, content.height, 0xCC000000);
        if (showInteractionChrome && !config.showGrid) {
            renderHoveredMarkerCoordinates(guiGraphics, content);
        }
    }

    private void renderBasesOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                  boolean showLabels, String localTeam) {
        if (visibleBases.isEmpty()) {
            return;
        }

        for (SyncBastionsMessage.BaseInfo base : visibleBases) {
            if (!isVisibleTeamMarker(localTeam, base.getTeam())) {
                continue;
            }

            BlockPos pos = base.getPos();
            if (!viewport.containsWorld(pos.getX(), pos.getZ())) {
                continue;
            }

            TacticalMapStaticProjection.ScreenPoint projected = staticProjection.point(
                "base:" + base.getTeam() + ":" + pos.asLong(),
                viewport.screenXd(pos.getX()), viewport.screenYd(pos.getZ()));
            double mapPosX = projected.x();
            double mapPosY = projected.y();
            int size = Math.round(BASE_MARKER_SIZE * getSelectedDeploymentScale(pos.getX(), pos.getZ()));
            int baseColor = getBastionColor(localTeam, base.getTeam());
            renderBaseMarker(guiGraphics, mapPosX, mapPosY, size, baseColor, base.getYaw());
            considerMarkerHover(mapPosX, mapPosY, size, pos.getX(), pos.getZ());

            if (!showLabels) {
                continue;
            }

            String name = base.getName() == null || base.getName().isEmpty() ? "主基地" : base.getName();
            renderMapLabel(guiGraphics, "base:" + base.getTeam() + ":" + pos.asLong(),
                2, name, mapPosX, mapPosY, size + 3, -size / 2, 0xFFFFFF);

        }
    }

    private void renderBastionsOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                     boolean showLabels, String localTeam) {
        if (visibleBastions.isEmpty()) {
            return;
        }

        for (SyncBastionsMessage.BastionInfo bastion : visibleBastions) {
            if (!isVisibleTeamMarker(localTeam, bastion.getTeam())) {
                continue;
            }

            BlockPos pos = bastion.getPos();
            if (!viewport.containsWorld(pos.getX(), pos.getZ())) {
                continue;
            }

            TacticalMapStaticProjection.ScreenPoint projected = staticProjection.point(
                "bastion:" + bastion.getTeam() + ":" + pos.asLong(),
                viewport.screenXd(pos.getX()), viewport.screenYd(pos.getZ()));
            double mapPosX = projected.x();
            double mapPosY = projected.y();
            int size = Math.round(BASTION_MARKER_SIZE * getSelectedDeploymentScale(pos.getX(), pos.getZ()));
            int bastionColor = getBastionColor(localTeam, bastion.getTeam());
            renderBastionMarker(guiGraphics, mapPosX, mapPosY, size, bastionColor, bastion);
            considerMarkerHover(mapPosX, mapPosY, size, pos.getX(), pos.getZ());

            if (!showLabels) {
                continue;
            }

            String fallback = bastion.isRally() ? "Rally"
                : (bastion.isHab() ? "HAB" : (bastion.isRadio() ? "Radio" : "FOB"));
            String name = bastion.getName() == null || bastion.getName().isEmpty() ? fallback : bastion.getName();
            if (bastion.isRally()) {
                name += " · " + bastion.getNextWaveSeconds() + "s";
            } else if (bastion.isRadio()) {
                name += " · 建材 " + bastion.getConstruction() + " / 弹药 " + bastion.getAmmunition();
            } else if (bastion.isHab()) {
                if (!bastion.isOperational()) {
                    name += " · 不可用";
                }
            } else {
                name += " · 建材 " + bastion.getConstruction() + " / 弹药 " + bastion.getAmmunition();
                if (!bastion.isOperational()) {
                    name += " · HAB不可用";
                }
            }
            renderMapLabel(guiGraphics, "bastion:" + bastion.getTeam() + ":" + pos.asLong(),
                3, name, mapPosX, mapPosY, size + 3, -size / 2, 0xFFFFFF);

        }
    }

    private void renderVehicleSupplyStationsOnMap(GuiGraphics guiGraphics, MapViewport viewport,
                                                  boolean showLabels, String localTeam) {
        if (visibleVehicleSupplyStations.isEmpty()) {
            return;
        }

        for (SyncBastionsMessage.VehicleSupplyStationInfo station : visibleVehicleSupplyStations) {
            if (!isVisibleTeamMarker(localTeam, station.getTeam())) {
                continue;
            }

            BlockPos pos = station.getPos();
            if (!viewport.containsWorld(pos.getX(), pos.getZ())) {
                continue;
            }

            TacticalMapStaticProjection.ScreenPoint projected = staticProjection.point(
                "station:" + station.getTeam() + ":" + pos.asLong(),
                viewport.screenXd(pos.getX()), viewport.screenYd(pos.getZ()));
            double mapPosX = projected.x();
            double mapPosY = projected.y();
            int size = Math.round(VEHICLE_SUPPLY_STATION_MARKER_SIZE
                * getSelectedDeploymentScale(pos.getX(), pos.getZ()));
            int color = getVehicleSupplyStationColor(localTeam, station.getTeam());
            renderVehicleSupplyStationMarker(guiGraphics, mapPosX, mapPosY, size, color);
            considerMarkerHover(mapPosX, mapPosY, size, pos.getX(), pos.getZ());

            if (!showLabels) {
                continue;
            }

            String name = station.getName() == null || station.getName().isEmpty()
                ? "载具补给站"
                : station.getName();
            renderMapLabel(guiGraphics, "station:" + station.getTeam() + ":" + pos.asLong(),
                3, name, mapPosX, mapPosY, size + 4, -size / 2, 0xFFFFFF);
        }
    }

    private void renderFobRadiiOnMap(GuiGraphics guiGraphics, MapViewport viewport, String localTeam) {
        for (SyncBastionsMessage.BastionInfo bastion : visibleBastions) {
            // 仅 Radio（及旧 FOB）画建造/排斥圈；HAB/Rally 不画
            if (bastion.isRally() || bastion.isHab()
                || !isVisibleTeamMarker(localTeam, bastion.getTeam())) {
                continue;
            }
            if (!bastion.isRadio() && bastion.getBuildRadius() <= 0.0
                && bastion.getExclusionRadius() <= 0.0) {
                continue;
            }
            BlockPos pos = bastion.getPos();
            if (bastion.getExclusionRadius() > 0.0) {
                renderWorldCircle(guiGraphics, viewport, pos.getX(), pos.getZ(),
                    bastion.getExclusionRadius(), 0x553F444A);
            }
            if (bastion.getBuildRadius() > 0.0) {
                renderWorldCircle(guiGraphics, viewport, pos.getX(), pos.getZ(),
                    bastion.getBuildRadius(), 0x9955AFFF);
            }
        }
    }

    private void renderWorldCircle(GuiGraphics guiGraphics, MapViewport viewport,
                                   double centerX, double centerZ, double radius, int color) {
        double pixelRadius = Math.max(radius * viewport.scaleX, radius * viewport.scaleZ);
        int segments = TacticalMapCircleMesh.segments(pixelRadius);
        double[] unit = TacticalMapCircleMesh.vertices(segments);
        double previousX = viewport.screenXd(centerX + unit[0] * radius);
        double previousY = viewport.screenYd(centerZ + unit[1] * radius);
        for (int index = 1; index <= segments; index++) {
            double currentX = viewport.screenXd(centerX + unit[index * 2] * radius);
            double currentY = viewport.screenYd(centerZ + unit[index * 2 + 1] * radius);
            drawClippedLine(guiGraphics, previousX, previousY, currentX, currentY, color, viewport);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private void renderBastionMarker(GuiGraphics guiGraphics, double x, double y, int size, int color,
                                     SyncBastionsMessage.BastionInfo bastion) {
        boolean enemy = isEnemyTint(color);
        ResourceLocation icon;
        if (bastion.isRally()) {
            icon = enemy ? MAP_EN_RALLY : MAP_RALLY;
        } else if (bastion.isHab()) {
            BlockPos pos = bastion.getPos();
            boolean selected = hasSelectedDeploymentPoint
                && matchesSelectedDeployment(pos.getX(), pos.getZ());
            icon = selected ? MAP_HAB_ACTIVATED : MAP_HAB;
        } else if (bastion.isRadio()) {
            icon = enemy ? MAP_EN_FOB : MAP_RADIO;
        } else {
            icon = enemy ? MAP_EN_FOB : MAP_FOB;
        }
        // HAB/Radio 贴图自带造型，己方用白色调制避免染色糊成一团
        int drawColor = enemy ? color : COLOR_FRIENDLY_WHITE;
        blitMapIcon(guiGraphics, x, y, size, icon, drawColor);
    }

    private boolean matchesSelectedDeployment(double worldX, double worldZ) {
        if (!hasSelectedDeploymentPoint) {
            return false;
        }
        double dx = selectedDeploymentX - worldX;
        double dz = selectedDeploymentZ - worldZ;
        return dx * dx + dz * dz <= SELECTED_DEPLOYMENT_MATCH_DISTANCE_SQUARED;
    }

    private static boolean isEnemyTint(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return r > 180 && g < 120 && b < 120;
    }

    private void blitMapIcon(GuiGraphics guiGraphics, double x, double y, int size,
                             ResourceLocation icon, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0D);
        try {
            int half = Math.max(4, size / 2);
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            float alpha = ((color >>> 24) & 0xFF) / 255.0F;
            if (alpha <= 0.01F) {
                alpha = 1.0F;
            }
            RenderSystem.setShaderColor(red, green, blue, alpha);
            int draw = half * 2;
            // 地图贴图统一按 128×128 源采样（旧 64 贴图会被拉伸，可接受）
            guiGraphics.blit(icon, -half, -half, draw, draw,
                0.0F, 0.0F, 128, 128, 128, 128);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.pose().popPose();
        }
    }

    private void renderVehicleSupplyStationMarker(GuiGraphics guiGraphics, double x, double y, int size, int color) {
        // 己方载具补给站使用专用扳手图标；敌方不在 isVisibleTeamMarker 中显示时不会走到这里。
        ResourceLocation icon = isEnemyTint(color) ? MAP_EN_ACP : MAP_VEHICLE_SUPPLY;
        int drawColor = isEnemyTint(color) ? color : COLOR_FRIENDLY_WHITE;
        blitMapIcon(guiGraphics, x, y, Math.max(size, 8), icon, drawColor);
    }

    private void renderBaseMarker(GuiGraphics guiGraphics, double x, double y, int size, int color, float yaw) {
        boolean enemy = isEnemyTint(color);
        ResourceLocation icon = enemy ? MAP_EN_FOB : MAP_MAINSPAWN;
        blitMapIcon(guiGraphics, x, y, Math.max(size, 10), icon, enemy ? color : COLOR_FRIENDLY_WHITE);
    }

    private void renderBaseMarkerAtOrigin(GuiGraphics guiGraphics, int size, int color, float yaw) {
        int half = Math.max(4, size / 2);
        int inner = Math.max(2, half / 2);
        guiGraphics.fill(-half, -half, half + 1, half + 1, 0xDD101010);
        guiGraphics.fill(-half + 1, -half + 1, half, half, color);
        guiGraphics.fill(-inner, -inner, inner + 1, inner + 1, 0xEE101010);

        double radians = Math.toRadians(yaw);
        int tipX = -(int) Math.round(Math.sin(radians) * half);
        int tipY = (int) Math.round(Math.cos(radians) * half);
        int pointerHalf = Math.max(1, size / 8);
        guiGraphics.fill(tipX - pointerHalf, tipY - pointerHalf, tipX + pointerHalf + 1, tipY + pointerHalf + 1, 0xFFFFFFFF);
    }

    private void renderPointMarker(GuiGraphics guiGraphics, double x, double y, int size, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0D);
        try {
            renderPointMarkerAtOrigin(guiGraphics, size, color);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private void renderPointMarkerAtOrigin(GuiGraphics guiGraphics, int size, int color) {
        int half = Math.max(1, size / 2);
        guiGraphics.fill(-half, -half, half + 1, half + 1, 0xDD101010);
        guiGraphics.fill(-half + 1, -half + 1, half, half, color);
    }

    private void renderMapLabel(GuiGraphics guiGraphics, String id, int priority,
                                String text, double x, double y,
                                int offsetX, int offsetY, int color) {
        if (id == null || !id.startsWith("capture:") || text == null || text.isBlank()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        float scale = getMapTextScale();
        String abbreviated = abbreviateMapLabel(text);
        int fullWidth = Math.max(1, Math.round(font.width(text) * scale));
        int abbreviatedWidth = Math.max(1, Math.round(font.width(abbreviated) * scale));
        int height = Math.max(1, Math.round(font.lineHeight * scale));
        TacticalMapLabelLayout.Placement placement = mapLabelLayout.place(
            id, priority, (int) Math.round(x), (int) Math.round(y),
            offsetX, offsetY, fullWidth, abbreviatedWidth, height);
        if (placement.mode() == TacticalMapLabelLayout.Mode.ICON_ONLY) {
            return;
        }
        String drawn = placement.mode() == TacticalMapLabelLayout.Mode.ABBREVIATED
            ? abbreviated : text;
        drawMapText(guiGraphics, drawn, placement.x(), placement.y(), color);
    }

    private static String abbreviateMapLabel(String text) {
        if (text == null || text.length() <= 10) {
            return text == null ? "" : text;
        }
        return text.substring(0, 9) + "…";
    }

    private int getMapTitleHeight(boolean showInteractionChrome) {
        if (!showInteractionChrome) {
            return 0;
        }
        return compactEmbeddedRendering ? EMBEDDED_MAP_TITLE_HEIGHT : MAP_TITLE_HEIGHT;
    }

    private float getMapTextScale() {
        return compactEmbeddedRendering ? EMBEDDED_TEXT_SCALE : 1.0F;
    }

    private void drawMapText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        float scale = getMapTextScale();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            text,
            Math.round(x / scale),
            Math.round(y / scale),
            color,
            false
        );
        guiGraphics.pose().popPose();
    }

    private int getTeamRelativeColor(String localTeam, String markerTeam) {
        String canonical = EspetroTeamBridge.canonicalizeTeamName(markerTeam);
        if (canonical == null || EspetroTeamBridge.isSameTeam(localTeam, markerTeam)) {
            return COLOR_FRIENDLY_WHITE;
        }
        return COLOR_ENEMY_RED;
    }

    private int getBastionColor(String team) {
        return COLOR_FRIENDLY_WHITE;
    }

    private int getBastionColor(String localTeam, String markerTeam) {
        return getTeamRelativeColor(localTeam, markerTeam);
    }

    private int getVehicleSupplyStationColor(String team) {
        return COLOR_FRIENDLY_WHITE;
    }

    private int getVehicleSupplyStationColor(String localTeam, String markerTeam) {
        return getTeamRelativeColor(localTeam, markerTeam);
    }

    private void renderTacticalMarkersOnMap(GuiGraphics guiGraphics, MapViewport viewport,
                                            boolean showLabels, String localTeam) {
        for (TacticalMarker marker : visibleTacticalMarkers) {
            if (!EspetroTeamBridge.isSameTeam(localTeam, marker.team())) {
                continue;
            }
            float opacity = getTacticalMarkerOpacity(marker);
            if (opacity <= 0.0F) {
                continue;
            }
            if (!viewport.containsWorld(marker.x(), marker.z())) {
                continue;
            }
            double mapX = viewport.screenXd(marker.x());
            double mapY = viewport.screenYd(marker.z());
            // 敌方单位：贴图已是红色，用白色 tint 避免叠色发暗；指令标用类型色
            int baseColor = switch (marker.type()) {
                case ATTACK_HERE -> TacticalMarkerType.ATTACK_HERE.getColor();
                case DEFEND_HERE -> TacticalMarkerType.DEFEND_HERE.getColor();
                case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                     ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> COLOR_FRIENDLY_WHITE;
                default -> com.example.espoints.tactical.TacticalMarkerIcons.isEnemyUnit(marker.type())
                    ? COLOR_FRIENDLY_WHITE : marker.type().getColor();
            };
            int fadedColor = withOpacity(baseColor, opacity);
            renderTacticalMarkerIcon(guiGraphics, mapX, mapY, marker.type(), fadedColor);
            considerMarkerHover(mapX, mapY, TACTICAL_MARKER_SIZE, marker.x(), marker.z());
            renderTacticalMarkerAnnotation(guiGraphics, marker, mapX, mapY, fadedColor, opacity);
        }
    }

    private void renderTacticalMarkerAnnotation(GuiGraphics guiGraphics, TacticalMarker marker,
                                                double mapX, double mapY, int fadedColor, float opacity) {
        TacticalMarkerType type = marker.type();
        if (type != TacticalMarkerType.ATTACK_HERE && type != TacticalMarkerType.DEFEND_HERE) {
            return;
        }
        int offset = TACTICAL_MARKER_SIZE / 2 + 3;
        if (marker.ownerCommander()) {
            int gold = withOpacity(0xFFFFC766, opacity);
            int r = Math.max(1, Math.round(1.5F * getMapTextScale()));
            int cx = (int) Math.round(mapX + offset);
            int cy = (int) Math.round(mapY - 1);
            guiGraphics.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, gold);
            return;
        }
        if (marker.ownerSquadId() > 0) {
            renderMapLabel(guiGraphics, "tactical:" + marker.id(), 4,
                String.valueOf(marker.ownerSquadId()), mapX, mapY,
                offset, -3, fadedColor);
        }
    }

    private float getTacticalMarkerOpacity(TacticalMarker marker) {
        if (marker.type().isPersistentUntilRemoved()) {
            return 1.0F;
        }
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        long duration = config.getTacticalMarkerDurationMillis();
        long remaining = duration - Math.max(0L, System.currentTimeMillis() - marker.createdAtMillis());
        if (remaining <= 0L) {
            return 0.0F;
        }
        long fadeDuration = config.getTacticalMarkerFadeMillis();
        // 与 3D 一致：仅在最后 fade 秒淡出，之前保持满不透明
        if (remaining >= fadeDuration) {
            return 1.0F;
        }
        return Mth.clamp(remaining / (float) Math.max(1L, fadeDuration), 0.0F, 1.0F);
    }

    private int withOpacity(int color, float opacity) {
        int originalAlpha = (color >>> 24) & 0xFF;
        int alpha = Mth.clamp(Math.round(originalAlpha * opacity), 0, 255);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
    
    /**
     * 在地图上渲染据点
     * @param guiGraphics GUI图形对象
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param alpha 透明度
     * @param player 本地玩家
     */
    private void renderCapturePointsOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                          boolean showLabels) {
        int markerSize = CAPTURE_POINT_MARKER_SIZE;
        int labelOffset = markerSize / 2 + 3;

        // 遍历所有据点
        for (CapturePoint point : allPoints) {
            // 计算据点中心坐标
            double pointCenterX = (point.getPos1().getX() + point.getPos2().getX()) / 2.0;
            double pointCenterZ = (point.getPos1().getZ() + point.getPos2().getZ()) / 2.0;

            TacticalMapStaticProjection.ScreenPoint projected = staticProjection.point(
                "capture:" + point.getName(),
                viewport.screenXd(pointCenterX), viewport.screenYd(pointCenterZ));
            double mapPosX = projected.x();
            double mapPosY = projected.y();
            // 根据据点状态获取颜色
            int pointColor = getStatusColor(point);
            
            // 渲染据点中心方块
            renderPointMarker(guiGraphics, mapPosX, mapPosY, markerSize, pointColor);
            considerMarkerHover(mapPosX, mapPosY, markerSize, pointCenterX, pointCenterZ);

            if (!showLabels) {
                renderPointBoundary(guiGraphics, point, viewport);
                continue;
            }

            // 渲染据点名称
            renderMapLabel(guiGraphics, "capture:" + point.getName(), 2,
                point.getName(), mapPosX, mapPosY, labelOffset, -6, 0xFFFFFF);
            
            // 渲染据点边界，传入网格边界参数，确保据点范围限制在网格内
            renderPointBoundary(guiGraphics, point, viewport);
        }

        renderBatchRoutes(guiGraphics, viewport);
    }

    private void renderBatchRoutes(GuiGraphics guiGraphics, MapViewport viewport) {
        CapturePoint previous = null;
        for (CapturePoint point : allPoints) {
            if (previous != null) {
                boolean newBatch = previous.getBatch() != point.getBatch();
                int color = newBatch ? ROUTE_COLORS[0] : SAME_BATCH_ROUTE_COLOR;
                drawRouteLine(guiGraphics, previous, point, color, viewport);
            }
            previous = point;
        }
    }

    private void drawRouteLine(GuiGraphics guiGraphics, CapturePoint from, CapturePoint to,
                               int color, MapViewport viewport) {
        double fromX = (from.getPos1().getX() + from.getPos2().getX()) / 2.0D;
        double fromZ = (from.getPos1().getZ() + from.getPos2().getZ()) / 2.0D;
        double toX = (to.getPos1().getX() + to.getPos2().getX()) / 2.0D;
        double toZ = (to.getPos1().getZ() + to.getPos2().getZ()) / 2.0D;
        drawClippedLine(guiGraphics, viewport.screenXd(fromX), viewport.screenYd(fromZ),
            viewport.screenXd(toX), viewport.screenYd(toZ), color, viewport);
    }
    
    /**
     * 渲染据点边界
     * @param guiGraphics GUI图形对象
     * @param point 据点对象
     * @param playerX 玩家X坐标
     * @param playerZ 玩家Z坐标
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param scaleX X轴缩放比例
     * @param scaleY Y轴缩放比例
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapRight 地图右下角X坐标
     * @param mapBottom 地图右下角Y坐标
     * @param alpha 透明度
     */
    private void renderPointBoundary(GuiGraphics guiGraphics, CapturePoint point, MapViewport viewport) {
        // 计算据点边界坐标
        int minX = Math.min(point.getPos1().getX(), point.getPos2().getX());
        int maxX = Math.max(point.getPos1().getX(), point.getPos2().getX());
        int minZ = Math.min(point.getPos1().getZ(), point.getPos2().getZ());
        int maxZ = Math.max(point.getPos1().getZ(), point.getPos2().getZ());

        if (maxX < viewport.viewMinX || minX > viewport.viewMaxX
                || maxZ < viewport.viewMinZ || minZ > viewport.viewMaxZ) {
            return;
        }

        int leftX = Mth.clamp(viewport.screenX(minX), viewport.left, viewport.right() - 1);
        int rightX = Mth.clamp(viewport.screenX(maxX), viewport.left, viewport.right() - 1);
        int topY = Mth.clamp(viewport.screenY(minZ), viewport.top, viewport.bottom() - 1);
        int bottomY = Mth.clamp(viewport.screenY(maxZ), viewport.top, viewport.bottom() - 1);

        // 根据据点状态获取颜色
        int boundaryColor = (getStatusColor(point) & 0x80FFFFFF); // 半透明

        if (leftX > rightX) {
            int tmp = leftX;
            leftX = rightX;
            rightX = tmp;
        }
        if (topY > bottomY) {
            int tmp = topY;
            topY = bottomY;
            bottomY = tmp;
        }

        if (leftX < rightX) {
            guiGraphics.fill(leftX, topY, rightX + 1, topY + 1, boundaryColor);
            guiGraphics.fill(leftX, bottomY, rightX + 1, bottomY + 1, boundaryColor);
        }
        if (topY < bottomY) {
            guiGraphics.fill(leftX, topY, leftX + 1, bottomY + 1, boundaryColor);
            guiGraphics.fill(rightX, topY, rightX + 1, bottomY + 1, boundaryColor);
        }
    }

    private MapViewport createViewport(TacticalMapJsonConfig config,
                                       TacticalMapJsonConfig.TacticalMapBounds bounds,
                                       double playerX,
                                       double playerZ,
                                       int mapLeft,
                                       int mapTop,
                                       int mapWidth,
                                       int mapHeight,
                                       int titleHeight) {
        int safeTitleHeight = Mth.clamp(titleHeight, 0, Math.max(0, mapHeight - 1));
        int availableHeight = Math.max(1, mapHeight - safeTitleHeight);
        // 视口矩形 = 整个地图框：不再按地图长宽比做 contain 居中适配，放大后地图可
        // 超出画幅填满整个框；最小缩放（整图）时靠统一的 X/Z 比例留白，无拉伸压缩。
        int width = Math.max(1, mapWidth);
        int height = Math.max(1, availableHeight);
        int left = mapLeft;
        int top = mapTop + safeTitleHeight;
        // 用实际内容矩形（剔除标题）的纵横比作为缩放比例，杜绝拉伸/压缩。
        contentAspectK = (double) height / width;

        ensureVisibleSpan(config, bounds);
        double[] span = getViewportSpan(bounds);
        double centerX;
        double centerZ;
        if (draggingMap || customMapCenter) {
            centerX = draggedCenterX;
            centerZ = draggedCenterZ;
        } else {
            double[] autoCenter = chooseAutoViewportCenter(playerX, playerZ, bounds, span[0], span[1]);
            centerX = autoCenter[0];
            centerZ = autoCenter[1];
        }
        double[] clamped = clampViewportCenter(centerX, centerZ, visibleWorldSpan, bounds);
        if (draggingMap || customMapCenter) {
            draggedCenterX = clamped[0];
            draggedCenterZ = clamped[1];
        }

        return new MapViewport(left, top, width, height, clamped[0], clamped[1], span[0], span[1], bounds);
    }

    private TacticalMapJsonConfig.TacticalMapBounds getEffectiveBounds(TacticalMapJsonConfig config,
                                                                       TacticalMapJsonConfig.TacticalMapBounds configuredBounds,
                                                                       LocalPlayer player) {
        return configuredBounds;
    }

    private double[] chooseAutoViewportCenter(double playerX,
                                             double playerZ,
                                             TacticalMapJsonConfig.TacticalMapBounds bounds,
                                             double spanX,
                                             double spanZ) {
        double[] playerCenter = clampViewportCenter(playerX, playerZ, visibleWorldSpan, bounds);
        return playerCenter;
    }

    private boolean containsAnyMarker(double centerX,
                                      double centerZ,
                                      double spanX,
                                      double spanZ,
                                      TacticalMapJsonConfig.TacticalMapBounds bounds,
                                      List<MarkerCenter> markers) {
        double minX = centerX - spanX / 2.0D;
        double maxX = centerX + spanX / 2.0D;
        double minZ = centerZ - spanZ / 2.0D;
        double maxZ = centerZ + spanZ / 2.0D;
        for (MarkerCenter marker : markers) {
            if (bounds.contains(marker.x, marker.z)
                    && marker.x >= minX && marker.x <= maxX
                    && marker.z >= minZ && marker.z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    private MarkerCenter findNearestMarker(double x,
                                           double z,
                                           TacticalMapJsonConfig.TacticalMapBounds bounds,
                                           List<MarkerCenter> markers) {
        MarkerCenter nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (MarkerCenter marker : markers) {
            if (!bounds.contains(marker.x, marker.z)) {
                continue;
            }
            double dx = marker.x - x;
            double dz = marker.z - z;
            double distance = dx * dx + dz * dz;
            if (distance < nearestDistance) {
                nearest = marker;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private List<MarkerCenter> collectSyncedMarkerCenters(LocalPlayer player, boolean includePlayers) {
        List<MarkerCenter> markers = new ArrayList<>();
        String localTeam = EspetroTeamBridge.getPlayerTeam(player);
        for (SyncBastionsMessage.BaseInfo base : visibleBases) {
            if (!isVisibleTeamMarker(localTeam, base.getTeam())) {
                continue;
            }
            BlockPos pos = base.getPos();
            addMarkerCenter(markers, pos.getX(), pos.getZ());
        }

        for (SyncBastionsMessage.BastionInfo bastion : visibleBastions) {
            if (!isVisibleTeamMarker(localTeam, bastion.getTeam())) {
                continue;
            }
            BlockPos pos = bastion.getPos();
            addMarkerCenter(markers, pos.getX(), pos.getZ());
        }

        for (CapturePoint point : allPoints) {
            addMarkerCenter(markers,
                (point.getPos1().getX() + point.getPos2().getX()) / 2.0D,
                (point.getPos1().getZ() + point.getPos2().getZ()) / 2.0D);
        }

        for (TacticalMarker marker : visibleTacticalMarkers) {
            if (EspetroTeamBridge.isSameTeam(localTeam, marker.team())
                    && getTacticalMarkerOpacity(marker) > 0.0F) {
                addMarkerCenter(markers, marker.x(), marker.z());
            }
        }

        if (!includePlayers) {
            return markers;
        }

        for (Map.Entry<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> entry
                : syncedPlayerPositions.entrySet()) {
            if (entry.getKey().equals(player.getUUID())) {
                continue;
            }
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition pos = entry.getValue();
            if (!EspetroTeamBridge.isSameTeam(localTeam, pos.getTeamName())) {
                continue;
            }
            addMarkerCenter(markers, pos.getX(), pos.getZ());
        }
        return markers;
    }

    private boolean isVisibleTeamMarker(String localTeam, String markerTeam) {
        String canonicalMarkerTeam = EspetroTeamBridge.canonicalizeTeamName(markerTeam);
        if (canonicalMarkerTeam == null) {
            return true;
        }
        return EspetroTeamBridge.isSameTeam(localTeam, canonicalMarkerTeam);
    }

    private void addMarkerCenter(List<MarkerCenter> markers, double x, double z) {
        if (Double.isFinite(x) && Double.isFinite(z)) {
            markers.add(new MarkerCenter(x, z));
        }
    }

    private double[] getViewportSpan(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        return getViewportSpan(bounds, visibleWorldSpan);
    }

    private double[] getViewportSpan(TacticalMapJsonConfig.TacticalMapBounds bounds, double span) {
        // 采用「地图框」的纵横比：spanZ = spanX * (boxH/boxW)，使 scaleX == scaleZ 恒定，
        // 不因框的长宽比而拉伸/压缩地图。
        double k = getEmbeddedViewportAspectRatio();
        return new double[] {span, span * k};
    }

    /**
     * 嵌入式地图（部署/小地图）视口的纵横比 = 地图框高/宽（boxH/boxW）。
     * 保证 X/Z 缩放一致、无变形；无嵌入式框时退回 1（按正方形处理）。
     */
    private double getEmbeddedViewportAspectRatio() {
        // 优先使用最近渲染帧的实际内容矩形比例（剔除标题），保证 scaleX==scaleZ。
        if (contentAspectK > 0.0D) {
            return contentAspectK;
        }
        if (lastEmbeddedMapWidth > 0 && lastEmbeddedMapHeight > 0) {
            return (double) lastEmbeddedMapHeight / lastEmbeddedMapWidth;
        }
        return 1.0D;
    }

    /**
     * 最小（整图）缩放对应的 span：在框纵横比 k 下，要看到整张地图需满足
     * spanX >= 地图宽 且 spanX * k >= 地图高。
     */
    private double wholeMapSpan(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double k = Math.max(0.001D, getEmbeddedViewportAspectRatio());
        return Math.max(bounds.width(), bounds.height() / k);
    }

    private double getMapDisplayAspectRatio(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        return Math.max(0.1D, Math.min(10.0D, bounds.aspectRatio()));
    }

    private void handleMapDrag(MapViewport viewport, TacticalMapJsonConfig.TacticalMapBounds bounds) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean leftPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double mouseX = getGuiMouseX(mc);
        double mouseY = getGuiMouseY(mc);
        boolean insideMap = viewport.containsScreen(mouseX, mouseY);
        boolean insideRecenterButton = isInsideLastRecenterButton(mouseX, mouseY);

        if (suppressMapDragUntilRelease) {
            draggingMap = false;
            if (!leftPressed) {
                suppressMapDragUntilRelease = false;
            }
            return;
        }

        if (leftPressed && insideRecenterButton && !draggingMap) {
            return;
        }

        if (leftPressed && insideMap && !draggingMap) {
            draggingMap = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartCenterX = viewport.centerX;
            dragStartCenterZ = viewport.centerZ;
            draggedCenterX = viewport.centerX;
            draggedCenterZ = viewport.centerZ;
            customMapCenter = true;
        }

        if (!leftPressed) {
            draggingMap = false;
            return;
        }

        if (draggingMap) {
            double dx = mouseX - dragStartMouseX;
            double dy = mouseY - dragStartMouseY;
            double[] clamped = clampViewportCenter(
                dragStartCenterX - dx / viewport.scaleX,
                dragStartCenterZ - dy / viewport.scaleZ,
                visibleWorldSpan,
                bounds
            );
            draggedCenterX = clamped[0];
            draggedCenterZ = clamped[1];
        }
    }

    private double getGuiMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    private double getGuiMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private boolean isInsideLastEmbeddedMap(double mouseX, double mouseY) {
        return lastEmbeddedMapLeft != Integer.MIN_VALUE
            && Minecraft.getInstance().screen == lastEmbeddedMapScreen
            && System.currentTimeMillis() - lastEmbeddedMapRenderMs <= 1000L
            && lastEmbeddedMapWidth > 0
            && lastEmbeddedMapHeight > 0
            && mouseX >= lastEmbeddedMapLeft
            && mouseX <= lastEmbeddedMapLeft + lastEmbeddedMapWidth
            && mouseY >= lastEmbeddedMapTop
            && mouseY <= lastEmbeddedMapTop + lastEmbeddedMapHeight;
    }

    private boolean isInsideLastRecenterButton(double mouseX, double mouseY) {
        return lastRecenterButtonLeft != Integer.MIN_VALUE
            && isInsideLastEmbeddedMap(mouseX, mouseY)
            && lastRecenterButtonWidth > 0
            && lastRecenterButtonHeight > 0
            && mouseX >= lastRecenterButtonLeft
            && mouseX <= lastRecenterButtonLeft + lastRecenterButtonWidth
            && mouseY >= lastRecenterButtonTop
            && mouseY <= lastRecenterButtonTop + lastRecenterButtonHeight;
    }

    private double[] clampViewportCenter(double centerX, double centerZ, double span,
                                         TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double[] viewportSpan = getViewportSpan(bounds, span);
        double spanX = viewportSpan[0];
        double spanZ = viewportSpan[1];

        double halfX = spanX / 2.0D;
        double halfZ = spanZ / 2.0D;
        double minCenterX = bounds.minX + halfX;
        double maxCenterX = bounds.maxX - halfX;
        double minCenterZ = bounds.minZ + halfZ;
        double maxCenterZ = bounds.maxZ - halfZ;

        if (minCenterX > maxCenterX) {
            centerX = bounds.centerX();
        } else {
            centerX = Mth.clamp(centerX, minCenterX, maxCenterX);
        }
        if (minCenterZ > maxCenterZ) {
            centerZ = bounds.centerZ();
        } else {
            centerZ = Mth.clamp(centerZ, minCenterZ, maxCenterZ);
        }
        return new double[] {centerX, centerZ};
    }

    private void renderMapBackground(GuiGraphics guiGraphics, MapViewport viewport) {
        renderConfiguredBackground(guiGraphics, viewport);
        guiGraphics.fill(viewport.left, viewport.top, viewport.right(), viewport.bottom(), 0x22000000);
    }

    private void renderConfiguredBackground(GuiGraphics guiGraphics, MapViewport viewport) {
        ClientTacticalMapTileCache cache = ClientTacticalMapTileCache.get();
        cache.drainUploadQueue(4, 6_000_000L);
        TacticalMapPyramidLayout layout = cache.layout();
        resetMapBlitColor(guiGraphics);
        guiGraphics.fill(viewport.left, viewport.top, viewport.right(), viewport.bottom(),
            MAP_BACKGROUND_FALLBACK_COLOR);
        if (layout == null) {
            mapLodPlanner.reset();
            cache.requestCurrentPreview();
            return;
        }

        double minX = (viewport.viewMinX - viewport.bounds.minX) / viewport.bounds.width();
        double maxX = (viewport.viewMaxX - viewport.bounds.minX) / viewport.bounds.width();
        double minY = (viewport.viewMinZ - viewport.bounds.minZ) / viewport.bounds.height();
        double maxY = (viewport.viewMaxZ - viewport.bounds.minZ) / viewport.bounds.height();
        lastViewMinX = minX;
        lastViewMinY = minY;
        lastViewMaxX = maxX;
        lastViewMaxY = maxY;
        lastViewScreenWidth = viewport.width;
        lastViewScreenHeight = viewport.height;
        TacticalMapLodPlanner.Plan plan = mapLodPlanner.planCached(
            layout,
            TacticalMapConfig.mapImageQuality.get(),
            new TacticalMapLodPlanner.Viewport(
                minX, minY, maxX, maxY, viewport.width, viewport.height),
            System.currentTimeMillis(),
            cache.textureBudgetBytes(),
            cache.descriptor().session(),
            cache.readinessRevision(),
            cache::tileState);
        // Rendering only publishes desire; client tick owns bounded network sends.
        cache.updateDesired(plan.desiredTiles());
        TacticalMapLodPlanner.Layer finestCovering = TacticalMapLayerPicker.finestCovering(
            layout, plan.layers(), cache::hasAll, minX, minY, maxX, maxY);
        if (finestCovering != null
            && blitLayer(guiGraphics, viewport, cache, layout, finestCovering)) {
            return;
        }

        // Nothing complete covers the view yet: one preview, never a mixed LOD.
        int previewLevel = layout.maxLevel();
        ClientTacticalMapTileCache.TextureEntry preview =
            cache.texture(previewLevel, 0, 0);
        if (preview == null) {
            cache.requestCurrentPreview();
        } else {
            renderTile(guiGraphics, viewport, layout, previewLevel, 0, 0, preview);
        }
    }

    private boolean blitLayer(GuiGraphics graphics, MapViewport viewport,
                              ClientTacticalMapTileCache cache,
                              TacticalMapPyramidLayout layout,
                              TacticalMapLodPlanner.Layer layer) {
        ClientTacticalMapTileCache.LayerAtlas atlas =
            cache.composeLayer(layer.level(), layer.visibleTiles());
        if (atlas != null) {
            TacticalMapTileScreenMath.IntRect dest = projectPixels(
                viewport, layout, layer.level(), atlas.spec().pixels());
            if (dest.isEmpty()) {
                return false;
            }
            blitTexture(graphics, atlas.texture(), dest, layer.level(), layout.maxLevel());
            return true;
        }
        for (TacticalMapPyramidLayout.TileCoordinate tile : layer.visibleTiles()) {
            ClientTacticalMapTileCache.TextureEntry texture =
                cache.texture(tile.level(), tile.x(), tile.y());
            if (texture == null) {
                return false;
            }
            renderTile(graphics, viewport, layout, tile.level(), tile.x(), tile.y(), texture);
        }
        return true;
    }

    private void renderTile(GuiGraphics graphics, MapViewport viewport,
                            TacticalMapPyramidLayout layout,
                            int level, int tileX, int tileY,
                            ClientTacticalMapTileCache.TextureEntry texture) {
        if (!layout.isValid(level, tileX, tileY)
            || texture.width() != layout.tileWidth(level, tileX)
            || texture.height() != layout.tileHeight(level, tileY)) {
            return;
        }
        TacticalMapTileScreenMath.IntRect dest = projectPixels(
            viewport, layout, level, TacticalMapTileScreenMath.tilePixels(layout, level, tileX, tileY));
        if (dest.isEmpty()) {
            return;
        }
        blitTexture(graphics, texture, dest, level, layout.maxLevel());
    }

    private TacticalMapTileScreenMath.IntRect projectPixels(
            MapViewport viewport, TacticalMapPyramidLayout layout,
            int level, TacticalMapTileScreenMath.PixelRect pixels) {
        return TacticalMapTileScreenMath.project(
            pixels, layout.levelWidth(level), layout.levelHeight(level),
            viewport.bounds.minX, viewport.bounds.minZ,
            viewport.bounds.width(), viewport.bounds.height(),
            viewport.viewMinX, viewport.viewMinZ,
            viewport.scaleX, viewport.scaleZ,
            viewport.left, viewport.top);
    }

    private void blitTexture(GuiGraphics graphics,
                             ClientTacticalMapTileCache.TextureEntry texture,
                             TacticalMapTileScreenMath.IntRect dest,
                             int level, int maximumLevel) {
        resetMapBlitColor(graphics);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture.gpuId());
        texture.prepareFiltering(level, maximumLevel,
            dest.width() / (double) texture.width(),
            dest.height() / (double) texture.height());
        TacticalMapTileScreenMath.BlitUv uv =
            TacticalMapTileScreenMath.insetUv(texture.width(), texture.height());
        graphics.blit(texture.location(), dest.left(), dest.top(),
            dest.width(), dest.height(),
            uv.uOffset(), uv.vOffset(), uv.uWidth(), uv.vHeight(),
            uv.textureWidth(), uv.textureHeight());
        resetMapBlitColor(graphics);
    }

    private static void resetMapBlitColor(GuiGraphics graphics) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 使用原版地图玩家标识（map_icons.png / {@link MapDecoration.Type#PLAYER}）。
     * <p>
     * 朝向：贴图尖端默认朝屏幕上方（-Y）。MC 的 yaw=0 为南方（+Z，地图下方），
     * 因此旋转角为 {@code yaw + 180}，与历史正确实现及原版地图观感一致。
     * 颜色通过 shader tint 乘到白底图标上（小队/指挥官色）。
     */
    private void renderMapPlayerIcon(GuiGraphics guiGraphics, double centerX, double centerY,
                                     float yaw, int size, int color) {
        MapDecoration.Type type = MapDecoration.Type.PLAYER;
        int icon = type.getIcon();
        int u = (icon % 16) * 8;
        int v = (icon / 16) * 8;
        float scale = Mth.clamp(size, 6, 28) / 8.0F;

        int argb = color;
        if (((argb >>> 24) & 0xFF) < 8) {
            argb |= 0xFF000000;
        }
        float red = ((argb >> 16) & 0xFF) / 255.0F;
        float green = ((argb >> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        float alpha = ((argb >>> 24) & 0xFF) / 255.0F;

        guiGraphics.pose().pushPose();
        try {
            guiGraphics.pose().translate(centerX, centerY, 200.0F);
            // 关键：原先缺 +180 会导致朝向与实际面对方向相反
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180.0F));
            guiGraphics.pose().scale(scale, scale, 1.0F);
            RenderSystem.setShaderColor(red, green, blue, alpha);
            guiGraphics.blit(VANILLA_MAP_ICONS, -4, -4, 8, 8,
                (float) u, (float) v, 8, 8, 128, 128);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.pose().popPose();
        }
    }

    private void renderTacticalMarkerIcon(GuiGraphics guiGraphics, double x, double y,
                                          TacticalMarkerType type) {
        renderTacticalMarkerIcon(guiGraphics, x, y, type, type.getColor());
    }

    private void renderTacticalMarkerIcon(GuiGraphics guiGraphics, double x, double y,
                                          TacticalMarkerType type, int color) {
        // 与主基地/兵站共用 blitMapIcon：size/2 为半宽，避免原先 half=SIZE 导致偏大一倍。
        blitMapIcon(guiGraphics, x, y, TACTICAL_MARKER_SIZE, mapTextureForMarkerType(type), color);
    }

    private static ResourceLocation mapTextureForMarkerType(TacticalMarkerType type) {
        return com.example.espoints.tactical.TacticalMarkerIcons.textureFor(type);
    }

    private void drawClippedLine(GuiGraphics guiGraphics, double x1, double y1, double x2, double y2,
                                 int color, MapViewport viewport) {
        double minX = viewport.left;
        double minY = viewport.top;
        double maxX = viewport.right() - 1;
        double maxY = viewport.bottom() - 1;

        int out1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
        int out2 = computeOutCode(x2, y2, minX, minY, maxX, maxY);

        int guard = 0;
        while (guard++ < 8) {
            if ((out1 | out2) == 0) {
                drawSmoothLine(guiGraphics, x1, y1, x2, y2, color);
                return;
            }
            if ((out1 & out2) != 0) {
                return;
            }

            int out = out1 != 0 ? out1 : out2;
            double x = 0.0D;
            double y = 0.0D;

            if ((out & 8) != 0) {
                if (y2 == y1) {
                    return;
                }
                x = x1 + (x2 - x1) * (maxY - y1) / (y2 - y1);
                y = maxY;
            } else if ((out & 4) != 0) {
                if (y2 == y1) {
                    return;
                }
                x = x1 + (x2 - x1) * (minY - y1) / (y2 - y1);
                y = minY;
            } else if ((out & 2) != 0) {
                if (x2 == x1) {
                    return;
                }
                y = y1 + (y2 - y1) * (maxX - x1) / (x2 - x1);
                x = maxX;
            } else if ((out & 1) != 0) {
                if (x2 == x1) {
                    return;
                }
                y = y1 + (y2 - y1) * (minX - x1) / (x2 - x1);
                x = minX;
            }

            if (out == out1) {
                x1 = x;
                y1 = y;
                out1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
            } else {
                x2 = x;
                y2 = y;
                out2 = computeOutCode(x2, y2, minX, minY, maxX, maxY);
            }
        }
    }

    private int computeOutCode(double x, double y, double minX, double minY, double maxX, double maxY) {
        int code = 0;
        if (x < minX) {
            code |= 1;
        } else if (x > maxX) {
            code |= 2;
        }
        if (y < minY) {
            code |= 4;
        } else if (y > maxY) {
            code |= 8;
        }
        return code;
    }

    private void drawSmoothLine(GuiGraphics guiGraphics, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 0.5D) {
            int x = (int) Math.round(x1);
            int y = (int) Math.round(y1);
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            return;
        }

        guiGraphics.pose().pushPose();
        try {
            guiGraphics.pose().translate(x1, y1, 0.0D);
            guiGraphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
            guiGraphics.fill(0, 0, Math.max(1, (int) Math.ceil(length)), 1, color);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private void resetVisibleSpan(TacticalMapJsonConfig config) {
        TacticalMapJsonConfig.TacticalMapBounds bounds = getCurrentBounds(config);
        visibleWorldSpan = wholeMapSpan(bounds);
        targetWorldSpan = visibleWorldSpan;
    }

    private void ensureVisibleSpan(TacticalMapJsonConfig config, TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double min = config.getMinimumRange(bounds);
        if (visibleWorldSpan <= 0.0D || Double.isNaN(visibleWorldSpan)) {
            visibleWorldSpan = wholeMapSpan(bounds);
        }
        // 上界为「整图可见」跨度：放大（span 减小）可自由超过旧 bounds.size()，缩小（看全图）停在此。
        visibleWorldSpan = Mth.clamp(visibleWorldSpan, min, wholeMapSpan(bounds));
        targetWorldSpan = targetWorldSpan <= 0.0D || Double.isNaN(targetWorldSpan)
            ? visibleWorldSpan : Mth.clamp(targetWorldSpan, min, wholeMapSpan(bounds));
    }

    private void preserveCustomViewportCenter(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        if (!customMapCenter && !draggingMap) {
            return;
        }

        double[] clamped = clampViewportCenter(draggedCenterX, draggedCenterZ, visibleWorldSpan, bounds);
        draggedCenterX = clamped[0];
        draggedCenterZ = clamped[1];
        customMapCenter = true;
    }

    private String getRangeText() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = getCurrentBounds(config);
        ensureVisibleSpan(config, bounds);
        return "范围:" + Math.round(visibleWorldSpan);
    }

    private TacticalMapJsonConfig.TacticalMapBounds getCurrentBounds(TacticalMapJsonConfig config) {
        return config.getBounds();
    }

    private void beginMarkerHover(boolean allowMouseInteraction, MapViewport viewport) {
        hoveredMapMarker = null;
        hoveredMapMarkerDistanceSquared = Double.MAX_VALUE;
        markerHoverEnabled = false;
        if (!allowMouseInteraction || markerWheel.active()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        markerHoverMouseX = getGuiMouseX(minecraft);
        markerHoverMouseY = getGuiMouseY(minecraft);
        markerHoverEnabled = viewport.containsScreen(markerHoverMouseX, markerHoverMouseY);
    }

    private void considerMarkerHover(double screenX, double screenY, int markerSize,
                                     double worldX, double worldZ) {
        if (!markerHoverEnabled) {
            return;
        }

        int hitRadius = Math.max(6, markerSize / 2 + 3);
        double dx = markerHoverMouseX - screenX;
        double dy = markerHoverMouseY - screenY;
        double distanceSquared = dx * dx + dy * dy;
        if (distanceSquared > hitRadius * hitRadius
                || distanceSquared >= hoveredMapMarkerDistanceSquared) {
            return;
        }

        hoveredMapMarkerDistanceSquared = distanceSquared;
        hoveredMapMarker = new HoveredMapMarker(Mth.floor(worldX), Mth.floor(worldZ));
    }

    private void renderHoveredMarkerCoordinates(GuiGraphics guiGraphics, MapViewport viewport) {
        if (hoveredMapMarker == null) {
            return;
        }

        String text = "X: " + hoveredMapMarker.x + "  Z: " + hoveredMapMarker.z;
        var font = Minecraft.getInstance().font;
        float textScale = getMapTextScale();
        int padding = compactEmbeddedRendering ? 2 : 4;
        int tooltipWidth = Math.round(font.width(text) * textScale) + padding * 2;
        int tooltipHeight = Math.round(font.lineHeight * textScale) + padding * 2;
        int minX = viewport.left;
        int maxX = Math.max(minX, viewport.right() - tooltipWidth);
        int minY = viewport.top;
        int maxY = Math.max(minY, viewport.bottom() - tooltipHeight);

        int tooltipX = Mth.clamp((int) Math.round(markerHoverMouseX) + 10, minX, maxX);
        int tooltipY = Mth.clamp((int) Math.round(markerHoverMouseY) + 10, minY, maxY);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xEE101018);
        guiGraphics.renderOutline(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xFFE0E0E0);
        drawMapText(guiGraphics, text, tooltipX + padding, tooltipY + padding, 0xFFFFFFFF);
    }

    private void renderSelectedDeploymentFrame(GuiGraphics guiGraphics, MapViewport viewport) {
        if (!hasSelectedDeploymentPoint
                || !viewport.containsWorld(selectedDeploymentX, selectedDeploymentZ)) {
            return;
        }

        double x = viewport.screenXd(selectedDeploymentX);
        double y = viewport.screenYd(selectedDeploymentZ);
        float progress = getSelectedDeploymentAnimationProgress();
        int frameSize = Math.round(Mth.lerp(progress,
            (float) SELECTED_DEPLOYMENT_FRAME_SIZE,
            (float) SELECTED_DEPLOYMENT_FRAME_MAX_SIZE));
        int half = frameSize / 2;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0D);
        try {
            guiGraphics.renderOutline(-half, -half, frameSize, frameSize, 0xFFFFFFFF);
            guiGraphics.renderOutline(-half + 1, -half + 1, frameSize - 2, frameSize - 2, 0xFFFFFFFF);
        } finally {
            guiGraphics.pose().popPose();
        }
        considerMarkerHover(x, y, frameSize,
            selectedDeploymentX, selectedDeploymentZ);
    }

    private float getSelectedDeploymentScale(double worldX, double worldZ) {
        if (!hasSelectedDeploymentPoint) {
            return 1.0F;
        }

        double dx = worldX - selectedDeploymentX;
        double dz = worldZ - selectedDeploymentZ;
        if (dx * dx + dz * dz > SELECTED_DEPLOYMENT_MATCH_DISTANCE_SQUARED) {
            return 1.0F;
        }

        return 1.0F + 0.35F * getSelectedDeploymentAnimationProgress();
    }

    private float getSelectedDeploymentAnimationProgress() {
        long now = System.currentTimeMillis();
        float progress = selectedDeploymentAnimationStartedAt <= 0L
            ? 1.0F
            : Mth.clamp((now - selectedDeploymentAnimationStartedAt)
                / (float) SELECTED_DEPLOYMENT_ANIMATION_MS, 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static final class HoveredMapMarker {
        private final int x;
        private final int z;

        private HoveredMapMarker(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * 战术地图上的实体位置：乘车时取根载具，步行时取自身。
     */
    private static Entity getMapRenderBody(Entity entity) {
        Entity root = entity.getRootVehicle();
        return root != null ? root : entity;
    }

    private static final class SmoothedPlayerMarker {
        private double fromX;
        private double fromZ;
        private float fromYaw;
        private double targetX;
        private double targetZ;
        private float targetYaw;
        private double renderX;
        private double renderZ;
        private float renderYaw;
        private double velocityX;
        private double velocityZ;
        private long interpolationStartedAt;
        private long interpolationDurationMs;
        private long lastUpdateAt;

        private SmoothedPlayerMarker(
                com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition position,
                long now) {
            snapTo(position, now);
        }

        private void updateTarget(
                com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition position,
                long now) {
            sample(now);
            double dx = position.getX() - renderX;
            double dz = position.getZ() - renderZ;
            if (dx * dx + dz * dz >= PLAYER_TELEPORT_DISTANCE_SQUARED) {
                snapTo(position, now);
                return;
            }

            long elapsedMs = Math.max(1L, now - lastUpdateAt);
            // 用上一目标到新采样估算速度，供插值结束后短时外推，避免到点冻结。
            double sampleDx = position.getX() - targetX;
            double sampleDz = position.getZ() - targetZ;
            velocityX = sampleDx / elapsedMs;
            velocityZ = sampleDz / elapsedMs;

            fromX = renderX;
            fromZ = renderZ;
            fromYaw = renderYaw;
            targetX = position.getX();
            targetZ = position.getZ();
            targetYaw = position.getYaw();
            interpolationStartedAt = now;
            long intervalMs = Math.max(MIN_PLAYER_INTERPOLATION_MS,
                Math.min(MAX_PLAYER_INTERPOLATION_MS, elapsedMs + PLAYER_INTERPOLATION_BUFFER_MS));
            interpolationDurationMs = intervalMs;
            lastUpdateAt = now;
        }

        private void sample(long now) {
            if (interpolationDurationMs <= 0L) {
                renderX = targetX;
                renderZ = targetZ;
                renderYaw = targetYaw;
                return;
            }

            long elapsed = now - interpolationStartedAt;
            if (elapsed <= interpolationDurationMs) {
                double progress = Mth.clamp(elapsed / (double) interpolationDurationMs, 0.0D, 1.0D);
                // 线性插值：高速载具下比 smoothstep 更匀速，减少“一顿一顿”。
                renderX = Mth.lerp(progress, fromX, targetX);
                renderZ = Mth.lerp(progress, fromZ, targetZ);
                renderYaw = fromYaw + (float) progress * Mth.wrapDegrees(targetYaw - fromYaw);
                return;
            }

            // 插值结束后短时 dead-reckoning：速度线性衰减，积分位移 = v*(t - t^2/(2T))
            long overshootMs = elapsed - interpolationDurationMs;
            if (overshootMs > MAX_PLAYER_EXTRAPOLATION_MS
                    || (velocityX == 0.0D && velocityZ == 0.0D)) {
                renderX = targetX;
                renderZ = targetZ;
                renderYaw = targetYaw;
                return;
            }

            double integratedMs = overshootMs - overshootMs * overshootMs
                / (2.0D * MAX_PLAYER_EXTRAPOLATION_MS);
            renderX = targetX + velocityX * integratedMs;
            renderZ = targetZ + velocityZ * integratedMs;
            renderYaw = targetYaw;
        }

        private void snapTo(
                com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition position,
                long now) {
            fromX = targetX = renderX = position.getX();
            fromZ = targetZ = renderZ = position.getZ();
            fromYaw = targetYaw = renderYaw = position.getYaw();
            velocityX = 0.0D;
            velocityZ = 0.0D;
            interpolationStartedAt = now;
            interpolationDurationMs = 0L;
            lastUpdateAt = now;
        }
    }

    private static final class MarkerCenter {
        private final double x;
        private final double z;

        private MarkerCenter(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private Object labelFrameKey(MapViewport content, TacticalMapJsonConfig config) {
        TacticalMapViewportQuantizer.LabelKey exact = TacticalMapViewportQuantizer.labelKey(
            staticMapRevision, content.viewMinX, content.viewMinZ,
            content.viewMaxX, content.viewMaxZ, content.spanX, content.spanZ,
            content.width, content.height, compactEmbeddedRendering, config.showLabels);
        long now = System.currentTimeMillis();
        if (draggingMap && lastLabelFrameKey != null && now - lastLabelLayoutMs < 50L) {
            return lastLabelFrameKey;
        }
        lastLabelFrameKey = exact;
        lastLabelLayoutMs = now;
        return exact;
    }

    private static final class MapViewport {
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final double centerX;
        private final double centerZ;
        private final double spanX;
        private final double spanZ;
        private final double viewMinX;
        private final double viewMinZ;
        private final double viewMaxX;
        private final double viewMaxZ;
        private final double scaleX;
        private final double scaleZ;
        private final TacticalMapJsonConfig.TacticalMapBounds bounds;

        private MapViewport(int left, int top, int width, int height, double centerX, double centerZ,
                            double spanX, double spanZ,
                            TacticalMapJsonConfig.TacticalMapBounds bounds) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.spanX = spanX;
            this.spanZ = spanZ;
            this.bounds = bounds;
            this.viewMinX = centerX - spanX / 2.0D;
            this.viewMinZ = centerZ - spanZ / 2.0D;
            this.viewMaxX = centerX + spanX / 2.0D;
            this.viewMaxZ = centerZ + spanZ / 2.0D;
            this.scaleX = width / spanX;
            this.scaleZ = height / spanZ;
        }

        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }

        private boolean containsWorld(double x, double z) {
            return x >= viewMinX && x <= viewMaxX && z >= viewMinZ && z <= viewMaxZ;
        }

        private boolean containsScreen(double x, double y) {
            return x >= left && x <= right() && y >= top && y <= bottom();
        }

        private int screenX(double worldX) {
            return (int) Math.round(screenXd(worldX));
        }

        private int screenY(double worldZ) {
            return (int) Math.round(screenYd(worldZ));
        }

        private double screenXd(double worldX) {
            return left + (worldX - viewMinX) * scaleX;
        }

        private double screenYd(double worldZ) {
            return top + (worldZ - viewMinZ) * scaleZ;
        }

        private double worldX(double screenX) {
            return Mth.clamp(viewMinX + (screenX - left) / scaleX, viewMinX, viewMaxX);
        }

        private double worldZ(double screenY) {
            return Mth.clamp(viewMinZ + (screenY - top) / scaleZ, viewMinZ, viewMaxZ);
        }
    }

    /**
     * 根据显示状态获取对应颜色
     * @param point 据点对象
     * @param alpha 透明度
     * @return 对应的颜色值（ARGB格式）
     */
    private int getStatusColor(CapturePoint point) {
        DisplayState displayState = point.getDisplayState();
        int baseColor = 0;
        
        switch (displayState) {
            case NEUTRAL:
                baseColor = 0xFFAAAAAA; // 灰色 - 中立状态
                break;
                
            case CAPTURING_FLAG_SINGLE:
                baseColor = 0xFFFFFF00; // 黄色 - 单人升旗状态
                break;
                
            case CAPTURING_CONTESTED_MULTI:
                baseColor = 0xFFFF8000; // 橙色 - 争夺中升旗状态
                break;
                
            case CONTESTED_MULTI:
                baseColor = 0xFFFF8000; // 橙色 - 争夺状态
                break;
                
            case CAPTURING_DOWN:
                baseColor = 0xFFFF0000; // 红色 - 降旗状态
                break;
                
            case CAPTURED:
                // 检查当前玩家是否属于占领者
                if (isFriendlyCapture(point)) {
                    baseColor = 0xFF55FF55; // 绿色 - 友方占领
                } else {
                    baseColor = 0xFFFF5555; // 红色 - 敌方占领
                }
                break;
                
            default:
                baseColor = 0xFFAAAAAA; // 默认灰色
        }
        
        return baseColor;
    }
    
    /**
     * 渲染队伍玩家列表
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param displayMode 地图显示模式
     */
    private void renderTeamPlayers(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, MapDisplayMode displayMode) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return;
        }
        
        String localTeam = EspetroTeamBridge.getPlayerTeam(localPlayer);
        if (localTeam == null) {
            return;
        }
        
        // 获取所有在线玩家
        List<net.minecraft.client.player.AbstractClientPlayer> allPlayers = minecraft.level.players();
        if (allPlayers.isEmpty()) {
            return;
        }
        
        // 创建玩家距离信息列表
        List<PlayerDistanceInfo> playerDistanceInfos = new ArrayList<>();
        
        // 计算每个玩家与本地玩家的距离
        for (net.minecraft.client.player.AbstractClientPlayer player : allPlayers) {
            if (!EspetroTeamBridge.isPlayerVisibleOnTacticalMap(player)) {
                continue;
            }
            if (!EspetroTeamBridge.isSameTeam(localTeam, EspetroTeamBridge.getPlayerTeam(player))) {
                continue; // 只显示同队伍玩家
            }
            
            double distance = localPlayer.distanceTo(player);
            playerDistanceInfos.add(new PlayerDistanceInfo(player, distance));
        }
        
        // 排序玩家，优先显示自己，然后按距离排序
        playerDistanceInfos.sort((a, b) -> {
            // 优先显示本地玩家
            if (a.player == localPlayer) {
                return -1;
            }
            if (b.player == localPlayer) {
                return 1;
            }
            // 其他玩家按距离排序
            return Double.compare(a.distance, b.distance);
        });
        
        // 选择最多4个玩家（包含本地玩家）
        List<PlayerDistanceInfo> selectedPlayers =
            playerDistanceInfos.subList(0, Math.min(4, playerDistanceInfos.size()));
        
        // 计算显示位置
        int textX, textY;
        boolean isLeftSide = displayMode == MapDisplayMode.ALWAYS_VISIBLE_BOTTOM_LEFT || displayMode == MapDisplayMode.ALWAYS_VISIBLE_TOP_LEFT;
        
        if (isLeftSide) {
            // 迷你地图在左侧，玩家列表显示在右侧
            textX = mapLeft + mapWidth + 5;
        } else {
            // 迷你地图在右侧，玩家列表显示在左侧
            int maxNameWidth = 0;
            for (PlayerDistanceInfo info : selectedPlayers) {
                int nameWidth = minecraft.font.width(info.player.getName().getString());
                if (nameWidth > maxNameWidth) {
                    maxNameWidth = nameWidth;
                }
            }
            textX = mapLeft - maxNameWidth - 25;
        }
        
        // 垂直居中显示
        int totalHeight = selectedPlayers.size() * 12;
        textY = mapTop + (mapHeight - totalHeight) / 2;
        
        // 获取攻防队伍和颜色
        ClientBattleState manager = ClientBattleState.get();
        String attackerTeam = manager.attackerTeam();
        String defenderTeam = manager.defenderTeam();
        
        // 获取颜色配置
        String defenderHexColor = TacticalMapConfig.defenderProgressBarColor.get();
        String attackerHexColor = TacticalMapConfig.attackerProgressBarColor.get();
        int defenderColor = com.example.espoints.util.ModLogger.hexToColor(defenderHexColor, 0xFF0055FF);
        int attackerColor = com.example.espoints.util.ModLogger.hexToColor(attackerHexColor, 0xFFFF5500);
        
        // 渲染玩家列表
        for (PlayerDistanceInfo info : selectedPlayers) {
            // 根据玩家血量确定小方块颜色
            float health = info.player.getHealth();
            float maxHealth = info.player.getMaxHealth();
            float healthPercentage = health / maxHealth;
            int playerColor;
            
            if (healthPercentage >= 0.6f) {
                // 100%~60%: 绿色
                playerColor = 0xFF00FF00;
            } else if (healthPercentage >= 0.25f) {
                // 59%~25%: 黄色
                playerColor = 0xFFFFFF00;
            } else {
                // 低于25%: 红色
                playerColor = 0xFFFF0000;
            }
            
            // 渲染血量状态方块
            guiGraphics.fill(textX, textY + 2, textX + 8, textY + 10, playerColor);
            
            // 渲染玩家名称
            guiGraphics.drawString(
                minecraft.font,
                info.player.getName().getString(),
                textX + 12,
                textY,
                0xFFFFFFFF,
                false
            );
            
            // 下移到下一个玩家位置
            textY += 12;
        }
    }
    
    /**
     * 玩家距离信息类
     */
    private static class PlayerDistanceInfo {
        private final net.minecraft.client.player.AbstractClientPlayer player;
        private final double distance;
        
        public PlayerDistanceInfo(net.minecraft.client.player.AbstractClientPlayer player, double distance) {
            this.player = player;
            this.distance = distance;
        }
    }
    
    /**
     * 渲染玩家箭头（表示朝向）
     * @param guiGraphics GUI图形对象
     * @param player 玩家对象
     * @param centerX 箭头中心X坐标
     * @param centerY 箭头中心Y坐标
     */
    private void renderPlayerArrow(GuiGraphics guiGraphics, LocalPlayer player, int centerX, int centerY) {
        int playerColor = 0xFFFF0000;
        
        // 获取玩家朝向（yRot）
        float playerYaw = player.getYRot();
        
        // Minecraft坐标系统到地图坐标系统的转换：
        // - yRot=0° 玩家面向北（正Z轴），地图上向上
        // - yRot=90° 玩家面向东（正X轴），地图上向右
        // - yRot=180° 玩家面向南（负Z轴），地图上向下
        // - yRot=270° 玩家面向西（负X轴），地图上向左
        
        // 箭头尺寸
        int arrowLength = 8;
        int arrowWidth = 3;
        
        // 计算箭头三个点的坐标
        int tipX, tipY, leftX, leftY, rightX, rightY;
        
        // 根据玩家朝向直接计算箭头方向，将四个方向反转
        tipX = centerX - (int)(Math.sin(Math.toRadians(playerYaw)) * arrowLength);
        tipY = centerY + (int)(Math.cos(Math.toRadians(playerYaw)) * arrowLength);
        
        // 计算箭头尾部的位置
        float tailAngle = playerYaw + 180;
        leftX = centerX - (int)(Math.sin(Math.toRadians(tailAngle + 30)) * arrowWidth);
        leftY = centerY + (int)(Math.cos(Math.toRadians(tailAngle + 30)) * arrowWidth);
        
        rightX = centerX - (int)(Math.sin(Math.toRadians(tailAngle - 30)) * arrowWidth);
        rightY = centerY + (int)(Math.cos(Math.toRadians(tailAngle - 30)) * arrowWidth);
        
        // 渲染箭头三角形
        drawTriangle(guiGraphics, tipX, tipY, leftX, leftY, rightX, rightY, playerColor);
    }
    
    /**
     * 绘制三角形
     * @param guiGraphics GUI图形对象
     * @param x1 点1 X坐标
     * @param y1 点1 Y坐标
     * @param x2 点2 X坐标
     * @param y2 点2 Y坐标
     * @param x3 点3 X坐标
     * @param y3 点3 Y坐标
     * @param color 颜色
     */
    private void drawTriangle(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // 使用Bresenham算法绘制三个边
        drawLine(guiGraphics, x1, y1, x2, y2, color);
        drawLine(guiGraphics, x2, y2, x3, y3, color);
        drawLine(guiGraphics, x3, y3, x1, y1, color);
        
        // 填充三角形内部
        // 找到y轴范围
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        
        // 遍历每一行
        for (int y = minY; y <= maxY; y++) {
            // 找到该行与三角形的左右交点
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            
            // 检查边1-2
            if ((y1 <= y && y < y2) || (y2 <= y && y < y1)) {
                int x = interpolate(x1, y1, x2, y2, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 检查边2-3
            if ((y2 <= y && y < y3) || (y3 <= y && y < y2)) {
                int x = interpolate(x2, y2, x3, y3, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 检查边3-1
            if ((y3 <= y && y < y1) || (y1 <= y && y < y3)) {
                int x = interpolate(x3, y3, x1, y1, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 填充该行
            if (left <= right) {
                guiGraphics.fill(left, y, right + 1, y + 1, color);
            }
        }
    }
    
    /**
     * 线性插值计算x坐标
     */
    private int interpolate(int x1, int y1, int x2, int y2, int y) {
        if (y1 == y2) return x1;
        return x1 + (x2 - x1) * (y - y1) / (y2 - y1);
    }
    
    /**
     * 渲染一条线
     * @param guiGraphics GUI图形对象
     * @param x1 起始X坐标
     * @param y1 起始Y坐标
     * @param x2 结束X坐标
     * @param y2 结束Y坐标
     * @param color 颜色
     */
    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        // 使用Bresenham算法绘制线
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        
        int err = dx - dy;
        int e2;
        
        int x = x1;
        int y = y1;
        
        while (true) {
            // 绘制当前点
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            
            // 检查是否到达终点
            if (x == x2 && y == y2) break;
            
            e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    /**
     * 检查占领是否为友方
     * @param point 据点对象
     * @return 如果是友方占领返回true，否则返回false
     */
    private boolean isFriendlyCapture(CapturePoint point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        
        String captorName = point.getCaptorName();
        if (captorName.isEmpty()) {
            return false;
        }
        
        return EspetroTeamBridge.isSameTeam(EspetroTeamBridge.getPlayerTeam(mc.player), captorName);
    }
    
    /**
     * 计算两个角度之间的差值（考虑角度环绕）
     * @param previous 上一帧的角度
     * @param current 当前帧的角度
     * @return 角度差值，范围在[-180, 180]之间
     */
    private float calculateAngleDelta(float previous, float current) {
        float delta = current - previous;
        // 确保差值在[-180, 180]范围内
        if (delta > 180.0f) {
            delta -= 360.0f;
        } else if (delta < -180.0f) {
            delta += 360.0f;
        }
        return delta;
    }
    
    /**
     * 获取战术地图HUD实例
     * @return 战术地图HUD实例
     */
    public static TacticalMapHUD getInstance() {
        return InstanceHolder.INSTANCE;
    }
    
    /**
     * 单例持有者
     */
    private static class InstanceHolder {
        private static final TacticalMapHUD INSTANCE = new TacticalMapHUD();
    }
}
