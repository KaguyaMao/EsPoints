package com.example.espoints.network;

import com.example.espoints.tactical.ArtilleryZoneState;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 火炮覆盖区域同步（服务端 → 本方客户端）。
 *
 * <p>Espetro 在炮击确认后通过反射调用 {@link #showZone}；本类把该队伍当前
 * 全部有效区域一次性广播给队员，客户端据此在小地图与 J 菜单大地图上绘制。</p>
 */
public class SyncArtilleryZonesMessage {

    /** 每队的有效区域列表（服务端内存态）。 */
    private static final Map<String, List<ArtilleryZoneState.Zone>> TEAM_ZONES = new HashMap<>();

    /** 覆盖区默认持续时长（毫秒）：与火炮脚本（2 发校射 + 6 轮覆盖）大致对齐。 */
    private static final long DEFAULT_DURATION_MS = 70_000L;

    private final List<ArtilleryZoneState.Zone> zones;

    public SyncArtilleryZonesMessage(List<ArtilleryZoneState.Zone> zones) {
        this.zones = zones == null ? List.of() : List.copyOf(zones);
    }

    public List<ArtilleryZoneState.Zone> zones() {
        return zones;
    }

    // ==================== 编解码 ====================

    public static void encode(SyncArtilleryZonesMessage message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.zones.size());
        for (ArtilleryZoneState.Zone zone : message.zones) {
            buf.writeUtf(zone.kind(), 16);
            buf.writeDouble(zone.x());
            buf.writeDouble(zone.z());
            buf.writeDouble(zone.endX());
            buf.writeDouble(zone.endZ());
            buf.writeDouble(zone.radius());
            buf.writeUtf(zone.icon() == null ? "" : zone.icon().toString(), 256);
            buf.writeVarLong(zone.endAtMs());
            buf.writeUtf(zone.dimension() == null ? "" : zone.dimension(), 128);
        }
    }

    public static SyncArtilleryZonesMessage decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 64);
        List<ArtilleryZoneState.Zone> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String kind = buf.readUtf(16);
            double x = buf.readDouble();
            double z = buf.readDouble();
            double endX = buf.readDouble();
            double endZ = buf.readDouble();
            double radius = buf.readDouble();
            String icon = buf.readUtf(256);
            long endAtMs = buf.readVarLong();
            String dimension = buf.readUtf(128);
            list.add(new ArtilleryZoneState.Zone(kind, x, z, endX, endZ, radius,
                icon.isBlank() ? null : ResourceLocation.tryParse(icon), endAtMs, dimension));
        }
        return new SyncArtilleryZonesMessage(list);
    }

    public static void handle(SyncArtilleryZonesMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                ArtilleryZoneState.replace(message.zones);
            }
        });
        context.setPacketHandled(true);
    }

    // ==================== 服务端入口（供 Espetro 反射调用） ====================

    /**
     * 显示/刷新某队伍的火炮覆盖区。
     *
     * @param team       队伍名（进攻/防守，大小写不敏感）
     * @param kind       "circle"（点选）或 "line"（徐进弹幕）
     * @param radius     覆盖半径（格）
     * @param durationMs 持续时长（&lt;=0 时用默认值）
     */
    public static void showZone(String team, String kind, double x, double z,
                                double endX, double endZ, double radius,
                                String icon, String dimension, long durationMs) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        String normalized = EspetroTeamBridge.canonicalizeTeamName(team);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long duration = durationMs > 0L ? durationMs : DEFAULT_DURATION_MS;
        ResourceLocation iconLocation = icon == null || icon.isBlank()
            ? null : ResourceLocation.tryParse(icon);

        synchronized (TEAM_ZONES) {
            List<ArtilleryZoneState.Zone> list = new ArrayList<>(
                TEAM_ZONES.getOrDefault(normalized, List.of()));
            list.removeIf((zone) -> zone.endAtMs() <= now);          // 清掉过期的
            list.add(new ArtilleryZoneState.Zone(
                "line".equalsIgnoreCase(kind) ? "line" : "circle",
                x, z, endX, endZ, Math.max(1.0D, radius), iconLocation, now + duration, dimension));
            while (list.size() > 8) {
                list.remove(0);
            }
            TEAM_ZONES.put(normalized, list);

            // 广播给该队伍在线玩家
            int sent = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String playerTeam = EspetroTeamBridge.canonicalizeTeamName(
                    EspetroTeamBridge.getServerPlayerTeam(player));
                if (playerTeam == null || !playerTeam.equals(normalized)) {
                    continue;
                }
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncArtilleryZonesMessage(List.copyOf(list)));
                sent++;
            }
        }
    }

    /** 清空某队伍的区域（例如换局）。 */
    public static void clearTeam(String team) {
        String normalized = EspetroTeamBridge.canonicalizeTeamName(team);
        if (normalized != null) {
            synchronized (TEAM_ZONES) {
                TEAM_ZONES.remove(normalized);
            }
        }
    }
}
