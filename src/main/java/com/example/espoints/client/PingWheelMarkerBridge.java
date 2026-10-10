package com.example.espoints.client;

import com.example.espoints.tactical.TacticalMarker;
import com.example.espoints.tactical.TacticalMarkerIcons;
import com.example.espoints.tactical.TacticalMarkerType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import nx.pingwheel.common.core.GameContext;
import nx.pingwheel.common.core.PingManager;
import nx.pingwheel.common.core.PingView;
import nx.pingwheel.common.config.ClientConfig;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将服务端权威 ESPoints 标点映射到 Ping Wheel 的 PingView。
 * 仅复用 Ping Wheel 的显示、距离缩放、方向指示与声音；可见范围和生命周期
 * 仍完全由 ESPoints 服务端决定。
 */
@OnlyIn(Dist.CLIENT)
public final class PingWheelMarkerBridge {

    private static final Map<UUID, PingView> VIEWS_BY_MARKER = new HashMap<>();
    private static final Map<PingView, TacticalMarkerType> TYPES_BY_VIEW =
        new IdentityHashMap<>();
    private static final ThreadLocal<PingView> CURRENT_RENDER = new ThreadLocal<>();
    private static int nextSequence = Integer.MIN_VALUE;

    private PingWheelMarkerBridge() {
    }

    public static void replaceSnapshot(List<TacticalMarker> markers) {
        clear();
        if (markers == null) {
            return;
        }
        for (TacticalMarker marker : markers) {
            add(marker, false);
        }
    }

    public static void add(TacticalMarker marker, boolean playSound) {
        if (marker == null || marker.id() == null || marker.type() == null) {
            return;
        }
        remove(marker.id());
        if (!isWithinPingDistance(marker)) {
            return;
        }
        PingView view = PingView.of(
            new Vec3(marker.x(), marker.y(), marker.z()),
            null,
            marker.ownerId(),
            nextSequence++,
            GameContext.getDimension());
        VIEWS_BY_MARKER.put(marker.id(), view);
        TYPES_BY_VIEW.put(view, marker.type());
        PingManager.addOrReplacePing(view);
        if (playSound) {
            view.playSoundInDimension();
        }
    }

    /**
     * Ping Wheel 在接收原生标点时只保留配置距离内的项目。ESPoints 服务端仍会
     * 同步整份同阵营战术状态给地图，因此客户端每秒轻量复核一次距离，让玩家
     * 移动进入/离开范围时保持同样行为（最多 64 次距离计算）。
     */
    public static void refreshVisibility(List<TacticalMarker> markers) {
        if (markers == null || markers.isEmpty()) {
            clear();
            return;
        }
        Map<UUID, TacticalMarker> byId = new HashMap<>(markers.size());
        for (TacticalMarker marker : markers) {
            if (marker != null && marker.id() != null) {
                byId.put(marker.id(), marker);
            }
        }
        for (UUID id : List.copyOf(VIEWS_BY_MARKER.keySet())) {
            TacticalMarker marker = byId.get(id);
            if (marker == null || !isWithinPingDistance(marker)) {
                remove(id);
            }
        }
        for (TacticalMarker marker : byId.values()) {
            if (!VIEWS_BY_MARKER.containsKey(marker.id())
                && isWithinPingDistance(marker)) {
                add(marker, false);
            }
        }
    }

    public static void remove(UUID markerId) {
        PingView view = VIEWS_BY_MARKER.remove(markerId);
        if (view == null) {
            return;
        }
        TYPES_BY_VIEW.remove(view);
        PingManager.PING_REPO.remove(view);
    }

    public static void clear() {
        if (!VIEWS_BY_MARKER.isEmpty()) {
            PingManager.PING_REPO.removeAll(VIEWS_BY_MARKER.values());
        }
        VIEWS_BY_MARKER.clear();
        TYPES_BY_VIEW.clear();
        CURRENT_RENDER.remove();
    }

    public static boolean isManaged(PingView view) {
        return view != null && TYPES_BY_VIEW.containsKey(view);
    }

    public static void beginRender(PingView view) {
        if (isManaged(view)) {
            CURRENT_RENDER.set(view);
        } else {
            CURRENT_RENDER.remove();
        }
    }

    public static void endRender() {
        CURRENT_RENDER.remove();
    }

    public static TacticalMarkerType currentType() {
        return TYPES_BY_VIEW.get(CURRENT_RENDER.get());
    }

    public static ResourceLocation currentTexture() {
        return TacticalMarkerIcons.textureFor(currentType());
    }

    public static int currentTint() {
        TacticalMarkerType type = currentType();
        if (type == null) {
            return 0xFFFFFFFF;
        }
        return switch (type) {
            case ATTACK_HERE, DEFEND_HERE -> type.getColor();
            case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                 ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> 0xFFFFFFFF;
            default -> TacticalMarkerIcons.isEnemyUnit(type) ? 0xFFFFFFFF : type.getColor();
        };
    }

    private static boolean isWithinPingDistance(TacticalMarker marker) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        int configured = ClientConfig.HANDLER.getConfig().getPingDistance();
        if (configured >= 2048) {
            return true;
        }
        double max = Math.max(0, configured);
        double dx = marker.x() - player.getX();
        double dy = marker.y() - player.getY();
        double dz = marker.z() - player.getZ();
        return dx * dx + dy * dy + dz * dz <= max * max;
    }
}
