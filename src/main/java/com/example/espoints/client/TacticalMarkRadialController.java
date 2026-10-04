package com.example.espoints.client;

import cc.sighs.auratip.api.action.Actions;
import cc.sighs.auratip.api.client.RadialMenuClientApi;
import cc.sighs.auratip.api.radiamenu.RadialMenuBuilder;
import cc.sighs.auratip.api.radiamenu.RadialMenuRegistry;
import cc.sighs.auratip.client.render.RadialMenuOverlay;
import cc.sighs.auratip.data.RadialMenuData;
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
import org.lwjgl.glfw.GLFW;
import nx.pingwheel.common.config.ClientConfig;
import nx.pingwheel.common.core.PingController;
import nx.pingwheel.common.math.Raycast;
import nx.pingwheel.common.util.InputUtils;
import org.espetro.client.gui.ClientGameState;
import org.espetro.team.GamePhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长按标点键 → AuraTip 轮盘（战术类型 + ESPoints 贴图）→ raycast 发包。
 * 标点状态仍只在 ESPoints；输入、射线和显示能力复用 Ping Wheel。
 *
 * <p>标点键（Ping Wheel 原版的 {@code key.pingwheel.ping_location}）在战局内按身份分流：
 * <ul>
 *   <li>普通成员：本控制器<b>完全不接管</b>按键，Ping Wheel 原版标点照常工作。</li>
 *   <li>可放置战术标点的玩家（指挥官 / 小队长 / 火力组长 / 合法载具座位）：
 *       短按（未到 {@link #OPEN_DELAY_TICKS} tick 就松开）补发一次 Ping Wheel 原版标点；
 *       长按打开战术标点轮盘；按住后松开确认悬停槽位。</li>
 * </ul>
 * 也就是说：所有人都能放原版标点，只有有权限的人能通过长按进入战术标点轮盘。
 *
 * <p>直接链接固定的 AuraTip（build.gradle 中按 {@code auratipJar} 解析），
 * 不使用反射 / 动态代理。动作处理器只注册一次，注册表被清理后仅重新发布菜单。
 * 所有标点槽位显式 {@code closeAfterAction=true}，选择后立即进入 AuraTip 关闭动画，
 * 关闭语义统一由槽位配置负责，动作处理器不再强制关闭轮盘。
 */
@OnlyIn(Dist.CLIENT)
public final class TacticalMarkRadialController {

    /** 按住超过该 tick 数才打开战术轮盘；在此之前松手 = 原版 Ping Wheel 标点。 */
    private static final int OPEN_DELAY_TICKS = 6;
    /**
     * 轮盘打开后至少再按住该 tick 数才在松手时确认槽位；
     * 否则按“慢速短按”处理：关闭轮盘并补发原版标点，避免误放战术标点。
     */
    private static final int RADIAL_MIN_HOLD_TICKS = 3;
    private static final String OWNER = "espoints_mark";
    private static final ResourceLocation MENU_ID =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "tactical_mark");
    private static final ResourceLocation PLACE_ACTION =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "place_tactical_mark");

    private static boolean initialized;
    private static boolean actionsRegistered;
    private static boolean keyWasDown;
    private static boolean ownsOverlay;
    private static boolean consumedUntilRelease;
    private static int heldTicks;
    private static long lastRequestMarkersMs;

    private TacticalMarkRadialController() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerActionsOnce();
        publishMenu();
        ModLogger.info("战术标点 AuraTip 轮盘已注册 (menu=" + MENU_ID + ")");
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
            ownsOverlay = false;
        });
    }

    private static void publishMenu() {
        RadialMenuRegistry.setMenus(OWNER, List.of(buildMenuData()));
    }

    static RadialMenuData buildMenuData() {
        RadialMenuBuilder builder = new RadialMenuBuilder(MENU_ID)
            .radii(44, 96)
            .animationSpeed(1.25f)
            .ringColors(List.of("#E6141719", "#F02A2D2F"));
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            // 敌方单位统一红；进攻黄 / 防守蓝
            String color = switch (type) {
                case ATTACK_HERE -> "#FFFFB52E";
                case DEFEND_HERE -> "#FF4D9DFF";
                case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                     ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> "#FFE05252";
                default -> "#FFE05252";
            };
            // 显式 closeAfterAction=true：选择后立即关闭；绝不使用 persistentSlot。
            builder = builder.slot(
                "espoints.mark." + type.name(),
                TacticalMarkerIcons.textureFor(type),
                Actions.script(PLACE_ACTION, Map.of("type", type.name())),
                Component.literal(type.getDisplayName()),
                color,
                true);
        }
        return builder.build();
    }

    public static void tick(Minecraft mc) {
        if (!initialized) {
            initialize();
        }
        if (mc == null || mc.player == null) {
            reset(false);
            return;
        }
        if (!isActiveBattlefield(mc)) {
            reset(false);
            keyWasDown = false;
            return;
        }
        // 只有能放置战术标点的玩家才接管标点键；其余玩家（普通成员）完全不干预，
        // Ping Wheel 原版标点（按住期间的默认行为）照常工作。
        if (!canLocalPlace()) {
            if (ownsOverlay) {
                closeOwnedOverlay();
            }
            reset(false);
            keyWasDown = false;
            return;
        }

        boolean down = InputUtils.KEY_BINDING_PING.isDown();
        if (!down) {
            boolean wasDown = keyWasDown;
            int held = heldTicks;
            keyWasDown = false;
            heldTicks = 0;
            if (wasDown) {
                if (ownsOverlay) {
                    if (held < OPEN_DELAY_TICKS + RADIAL_MIN_HOLD_TICKS) {
                        // 轮盘刚打开就松手：按短按处理，关闭轮盘并补发原版标点。
                        closeOwnedOverlay();
                        queueOriginalPing(mc);
                    } else {
                        finishSelection(mc);
                    }
                } else if (!consumedUntilRelease && held < OPEN_DELAY_TICKS) {
                    // 短按（未进入轮盘）：把这一次按键交还给 Ping Wheel 原版标点。
                    queueOriginalPing(mc);
                }
            }
            consumedUntilRelease = false;
            return;
        }

        keyWasDown = true;
        // heldTicks 统计整个按住时长（含轮盘已打开期间），松手时用它区分短按与确认。
        heldTicks++;
        if (consumedUntilRelease || mc.screen != null) {
            return;
        }
        if (ownsOverlay) {
            return;
        }
        if (heldTicks < OPEN_DELAY_TICKS) {
            return;
        }
        // 打开轮盘前拉一次标点快照，保证 3D 与地图有数据
        requestMarkersIfStale();

        if (openAuraMenu()) {
            ownsOverlay = true;
        } else {
            mc.player.displayClientMessage(
                Component.literal("§c无法打开标点轮盘（菜单未注册或 AuraTip 异常）。"), true);
            consumedUntilRelease = true;
        }
    }

    /**
     * 把被压制的这一次按键补发成 Ping Wheel 原版标点。
     * 按键已松开后 {@link #shouldSuppressDefaultPing()} 放行，排队会在下一个
     * client tick start 由 Ping Wheel 正常发射。
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

    /** 关闭本控制器拥有的 AuraTip 轮盘（不确认悬停槽位）。 */
    private static void closeOwnedOverlay() {
        if (RadialMenuOverlay.INSTANCE.isActive()) {
            RadialMenuOverlay.INSTANCE.close();
        }
        ownsOverlay = false;
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
     * PingController mixin 使用：只在“本玩家接管标点键”期间压制默认 Ping Wheel 标点。
     *
     * <p>必须仍在按住标点键：短按松开后由 {@link #tick(Minecraft)} 补发一次原版标点，
     * 若此时继续压制，补发的标点会被吃掉。
     */
    public static boolean shouldSuppressDefaultPing() {
        if (!isActiveBattlefield(Minecraft.getInstance())) {
            return false;
        }
        if (!InputUtils.KEY_BINDING_PING.isDown()) {
            return false;
        }
        return canLocalPlace();
    }

    static List<String> menuSlotIds() {
        List<String> ids = new ArrayList<>();
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            ids.add("espoints.mark." + type.name());
        }
        return ids;
    }

    private static boolean isActiveBattlefield(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        GamePhase phase = ClientGameState.getCurrentPhase();
        return (phase == GamePhase.DEPLOYING || phase == GamePhase.BATTLE)
            && !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension());
    }

    private static void finishSelection(Minecraft mc) {
        if (!ownsOverlay) {
            reset(false);
            return;
        }
        // 松开按键时确认当前悬停槽位；有效选择只发送一次标点请求，随后由
        // AuraTip 依槽位 closeAfterAction 正常关闭；空白区域由 AuraTip 判定关闭。
        if (RadialMenuOverlay.INSTANCE.isActive()) {
            double mouseX = mc.mouseHandler.xpos()
                * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos()
                * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            RadialMenuOverlay.INSTANCE.mouseClicked(
                mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        reset(true);
    }

    private static void reset(boolean keepConsumed) {
        heldTicks = 0;
        ownsOverlay = false;
        if (!keepConsumed) {
            consumedUntilRelease = false;
        }
    }

    private static boolean openAuraMenu() {
        if (!ensureMenusRegistered()) {
            return false;
        }
        try {
            RadialMenuClientApi.open(MENU_ID);
            return true;
        } catch (Throwable t) {
            ModLogger.warn("打开标点轮盘失败: " + t);
            return false;
        }
    }

    private static boolean ensureMenusRegistered() {
        if (RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null) {
            return true;
        }
        // 注册表被其他模组 clearAll/clear 后：只重新发布菜单，不重复注册动作处理器。
        publishMenu();
        return RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null;
    }

    private static void placeAtLook(TacticalMarkerType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || type == null) {
            return;
        }
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float pt = mc.getFrameTime();
        Vec3 look = cam.getViewVector(pt);
        ClientConfig pingConfig = ClientConfig.HANDLER.getConfig();
        double maxReach = Math.min(256.0D,
            Math.min(pingConfig.getRaycastDistance(), pingConfig.getPingDistance()));
        HitResult hit = Raycast.traceDirectional(
            look, pt, maxReach, cam.isCrouching());
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            mc.player.displayClientMessage(
                Component.literal("§7准星没有指向可标记位置。"), true);
            return;
        }
        Vec3 pos = hit.getLocation().add(0, 0.25, 0);
        NetworkHandler.INSTANCE.sendToServer(
            new PlaceTacticalMarkerMessage(type, pos.x, pos.y, pos.z));
    }
}