package com.example.espoints.client;

import org.esradial.client.Actions;
import org.esradial.client.RadialCommandMenu;
import org.esradial.client.RadialMenuClientApi;
import org.esradial.client.RadialMenuBuilder;
import org.esradial.client.RadialMenuRegistry;
import org.esradial.client.RadialMenuData;
import org.esradial.core.RadialLayout;
import com.example.espoints.ESPointsMod;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.PlaceTacticalMarkerMessage;
import com.example.espoints.network.RequestTacticalMarkersMessage;
import com.example.espoints.tactical.ClientTacticalMarkerState;
import com.example.espoints.tactical.TacticalMarkerIcons;
import com.example.espoints.tactical.TacticalMarkerType;
import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import nx.pingwheel.common.config.ClientConfig;
import nx.pingwheel.common.core.PingController;
import nx.pingwheel.common.math.Raycast;
import nx.pingwheel.common.util.InputUtils;
import org.espetro.client.gui.ClientGameState;
import org.espetro.team.GamePhase;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 长按标点键打开共享分类轮盘；左键进入目录/确认，松开取消，右键返回。
 * 标点状态仍只在 ESPoints，输入、射线和显示能力复用 Ping Wheel。
 * 菜单使用独立 owner，关闭或离开战场时不会影响 Espetro 的轮盘。
 */
@OnlyIn(Dist.CLIENT)
public final class TacticalMarkRadialController {

    private static final int OPEN_DELAY_TICKS = 4;
    private static final String OWNER = "espoints_mark";
    private static final ResourceLocation MENU_ID =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "tactical_mark");
    private static final ResourceLocation PLACE_ACTION =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "place_tactical_mark");

    private static boolean initialized;
    private static boolean actionsRegistered;
    private static boolean consumedUntilRelease;
    private static int heldTicks;
    private static long lastRequestMarkersMs;
    private static Vec3 openingTarget;

    private TacticalMarkRadialController() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerActionsOnce();
        publishMenu();
        RadialCommandMenu.setOrder("espoints.directory.enemy", 0);
        RadialCommandMenu.setOrder("espoints.directory.orders", 200);
        RadialCommandMenu.register(OWNER, () -> RadialMenuRegistry.getRuntimeMenu(MENU_ID),
            () -> isActiveBattlefield(Minecraft.getInstance()) && canLocalPlace(), () -> {
                requestMarkersIfStale(); captureOpeningTarget();
            });
        ModLogger.info("战术标点 EsRadial 轮盘已注册 (menu=" + MENU_ID + ")");
    }

    /**
     * 动作处理器只注册一次，与菜单发布解耦。
     * {@link RadialMenuRegistry} 被其他模组清空后，只需重新发布菜单，避免重复注册处理器。
     */
    private static void registerActionsOnce() {
        if (actionsRegistered) {
            return;
        }
        actionsRegistered = true;
        Actions.register(PLACE_ACTION, params -> {
            try {
                placeAtLook(TacticalMarkerType.valueOf(params.getString("type", "")));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            // 关闭动画由槽位 closeAfterAction 负责；这里只拦截同一次按键期间的
            // 二次确认（直接鼠标点击后再松开标点键不会重复发包）。
            consumedUntilRelease = true;
        });
    }

    private static void publishMenu() {
        RadialMenuRegistry.setMenus(OWNER, buildMenuTree(TacticalMarkRadialController::placeAtLook,
            id -> {
                var next = RadialMenuRegistry.getRuntimeMenu(id);
                if (next != null) RadialMenuClientApi.navigate(next);
            }));
    }

    public static List<RadialMenuData> buildMenuTree(java.util.function.Consumer<TacticalMarkerType> place,
                                                   java.util.function.Consumer<ResourceLocation> navigate) {
        var menus = new java.util.ArrayList<RadialMenuData>();
        menus.add(buildPage("", "战术标点", place, navigate));
        for (var directory : TacticalMarkCatalog.directories())
            menus.add(buildPage(directory.id(), directory.title(), place, navigate));
        return List.copyOf(menus);
    }
    public static ResourceLocation pageId(String directory) {
        return directory.isEmpty() ? MENU_ID : ResourceLocation.fromNamespaceAndPath("espoints", "mark_" + directory);
    }
    private static RadialMenuData buildPage(String id, String title,
            java.util.function.Consumer<TacticalMarkerType> place,
            java.util.function.Consumer<ResourceLocation> navigate) {
        var builder = new RadialMenuBuilder(pageId(id)).title(Component.literal(title))
            .radii(44, 96).squadLayout().animationSpeed(1.25f)
            .ringColors(List.of("#B824292B", "#C832383A"));
        for (var child : TacticalMarkCatalog.children(id)) {
            builder.slot("espoints.directory." + child.id(),
                ResourceLocation.fromNamespaceAndPath("esradial", "textures/squad/" + child.icon() + ".png"),
                () -> navigate.accept(pageId(child.id())), Component.literal(child.title()), "#FFD5B25C", false)
                .submenuLast();
        }
        TacticalMarkCatalog.directories().stream().filter(d -> d.id().equals(id)).findFirst().ifPresent(d -> {
            for (TacticalMarkerType type : d.marks()) builder.slot("espoints.mark." + type.name(),
                TacticalMarkerIcons.textureFor(type), () -> place.accept(type),
                Component.literal(type.getDisplayName()), type.name().startsWith("ENEMY_") ? "#FFE05252" : "#FFD5B25C");
        });
        return builder.build();
    }

    static RadialMenuData buildMenuData() {
        return buildMenuTree(TacticalMarkRadialController::placeAtLook, id -> {
            var next = RadialMenuRegistry.getRuntimeMenu(id);
            if (next != null) RadialMenuClientApi.navigate(next);
        }).get(0);
    }

    /** 不初始化 Minecraft 注册表的菜单描述，布局与运行时菜单共用。 */
    record MenuEntry(TacticalMarkerType type, String id, ResourceLocation icon,
                     String color, boolean closeAfterAction) { }

    static RadialLayout menuLayout() {
        return RadialLayout.squad(44, 96, TacticalMarkerType.selectableValues().length);
    }

    static List<MenuEntry> menuEntries() {
        return Arrays.stream(TacticalMarkerType.selectableValues()).map(type -> {
            String color = switch (type) {
                case ATTACK_HERE -> "#FFFFB52E";
                case DEFEND_HERE -> "#FF4D9DFF";
                case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                     ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> "#FFE05252";
                default -> "#FFE05252";
            };
            return new MenuEntry(type, "espoints.mark." + type.name(),
                TacticalMarkerIcons.textureFor(type), color, true);
        }).toList();
    }

    public static void tick(Minecraft mc) {
        if (!initialized) {
            initialize();
        }
        if (mc == null || mc.player == null) {
            reset();
            return;
        }
        if (!isActiveBattlefield(mc)) {
            reset();
            return;
        }
        // 只有能放置战术标点的玩家才接管标点键；普通成员完全不干预，
        // Ping Wheel 原版标点（按住期间的默认行为）照常工作。
        if (!canLocalPlace()) {
            heldTicks = 0;
            consumedUntilRelease = false;
            return;
        }
        // Ping Wheel 在 tick start 已经排队；战局内由本轮盘接管，立即撤销默认标点。
        PingController.revokePingAction();
        boolean down = InputUtils.KEY_BINDING_PING.isDown();
        if (!down) {
            int held = heldTicks;
            boolean wasDown = held > 0;
            boolean consumed = consumedUntilRelease;
            heldTicks = 0;
            consumedUntilRelease = false;
            // 短按（未到 OPEN_DELAY_TICKS、也没被轮盘占用）：把这次按键交还 Ping Wheel 原版标点。
            if (wasDown && !consumed && held < OPEN_DELAY_TICKS && mc.screen == null) {
                queueOriginalPing(mc);
            }
            return;
        }
        if (consumedUntilRelease || mc.screen != null) {
            return;
        }
        if (RadialMenuClientApi.isOwnedBy(OWNER)) {
            return;
        }
        // 共用一个轮盘；已有 Espetro 菜单时不抢占，也不在本次长按中重试。
        if (RadialMenuClientApi.isActive()) {
            consumedUntilRelease = true;
            return;
        }
        heldTicks++;
        if (heldTicks < OPEN_DELAY_TICKS) {
            return;
        }
        if (!canLocalPlace()) {
            mc.player.displayClientMessage(
                Component.literal("§c当前身份不能放置战术标点。"), true);
            consumedUntilRelease = true;
            return;
        }
        // 打开轮盘前拉一次标点快照，保证 3D 与地图有数据
        requestMarkersIfStale();

        if (!openRadialMenu()) {
            mc.player.displayClientMessage(
                Component.literal("§c无法打开标点轮盘（菜单未注册或 EsRadial 异常）。"), true);
            consumedUntilRelease = true;
        }
    }

    /**
     * 把这一次按键交还给 Ping Wheel 原版标点。
     * <p>松键后 {@link #tick} 不再撤销动作，Ping Wheel 会在下一个 client tick 正常处理。</p>
     */
    private static void queueOriginalPing(Minecraft mc) {
        if (mc == null || mc.screen != null) {
            return;
        }
        try {
            PingController.queuePingAction();
        } catch (Throwable t) {
            ModLogger.warn("补发原版标点失败: " + t);
        }
    }

    private static void requestMarkersIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMarkersMs < 1500L) {
            return;
        }
        // 增量同步正常时无需周期性拉取完整列表；只在本地完全无状态时恢复一次。
        if (ClientTacticalMarkerState.getMarkers().isEmpty()) {
            lastRequestMarkersMs = now;
            NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMarkersMessage());
        }
    }

    private static boolean canLocalPlace() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        return EspetroTeamBridge.canPlaceTacticalMarkerClientHint(p);
    }

    /**
     * PingController mixin 使用：只在"本地玩家确有标点权限"且"正在按住标点键"时接管默认 Ping Wheel。
     * <p>普通成员（无权限）完全不压制 ⇒ 原版标点照常可用；
     * 松开瞬间不再压制 ⇒ 短按补发的原版标点能被 poll 到。</p>
     */
    public static boolean shouldSuppressDefaultPing() {
        Minecraft mc = Minecraft.getInstance();
        if (!isActiveBattlefield(mc) || mc.player == null) {
            return false;
        }
        return EspetroTeamBridge.canPlaceTacticalMarkerClientHint(mc.player)
            && InputUtils.KEY_BINDING_PING.isDown();
    }

    static List<String> menuSlotIds() {
        return menuEntries().stream().map(MenuEntry::id).toList();
    }

    private static boolean isActiveBattlefield(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        GamePhase phase = ClientGameState.getCurrentPhase();
        return (phase == GamePhase.DEPLOYING || phase == GamePhase.BATTLE)
            && !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension());
    }

    private static void reset() {
        // 使用 owner 限定关闭范围；退出战场、断线时不确认旧悬停项。
        RadialMenuClientApi.close(OWNER);
        heldTicks = 0;
        consumedUntilRelease = false;
    }

    private static boolean openRadialMenu() {
        if (!ensureMenusRegistered()) {
            return false;
        }
        try {
            return RadialMenuClientApi.open(RadialCommandMenu.compose(RadialMenuRegistry.getRuntimeMenu(MENU_ID)),
                new RadialMenuClientApi.OpenOptions(OWNER,
                    () -> InputUtils.KEY_BINDING_PING.isDown(), false,
                    reason -> consumedUntilRelease = true));
        } catch (RuntimeException error) {
            ModLogger.warn("打开标点轮盘失败: " + error);
            return false;
        }
    }

    private static boolean ensureMenusRegistered() {
        if (RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null) {
            return true;
        }
        // 注册表被其他模组 clear 后：只重新发布菜单，不重复注册动作处理器。
        publishMenu();
        return RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null;
    }

    private static void placeAtLook(TacticalMarkerType type) {
        Minecraft mc = Minecraft.getInstance();
        if (type == null || !isActiveBattlefield(mc) || !canLocalPlace()) {
            return;
        }
        if (openingTarget == null) {
            mc.player.displayClientMessage(Component.literal("§7打开轮盘时没有指向可标记位置。"), true);
            return;
        }
        Vec3 pos = openingTarget;
        NetworkHandler.INSTANCE.sendToServer(new PlaceTacticalMarkerMessage(type, pos.x, pos.y, pos.z));
        consumedUntilRelease = true;

    }
    private static void captureOpeningTarget() {
        openingTarget = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float pt = mc.getFrameTime();
        Vec3 look = cam.getViewVector(pt);
        ClientConfig pingConfig = ClientConfig.HANDLER.getConfig();
        double maxReach = Math.min(256.0D,
            Math.min(pingConfig.getRaycastDistance(), pingConfig.getPingDistance()));
        HitResult hit = Raycast.traceDirectional(
            look, pt, maxReach, cam.isCrouching());
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        openingTarget = hit.getLocation().add(0, 0.25, 0);
    }

}
