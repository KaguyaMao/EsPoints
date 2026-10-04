package com.example.espoints.network;

import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Small map descriptor; full PNG bytes are never sent or decoded on open. */
public final class SyncTacticalMapBackgroundMessage {
    private static final String CLIENT_CACHE_CLASS =
        "com.example.espoints.client.ClientTacticalMapTileCache";
    private final TacticalMapTileService.Descriptor descriptor;

    private SyncTacticalMapBackgroundMessage(TacticalMapTileService.Descriptor descriptor) {
        this.descriptor = descriptor == null
            ? TacticalMapTileService.Descriptor.EMPTY
            : descriptor;
    }

    public static void encode(SyncTacticalMapBackgroundMessage message, FriendlyByteBuf buf) {
        TacticalMapTileService.Descriptor descriptor = message.descriptor;
        buf.writeBoolean(descriptor.present());
        if (!descriptor.present()) {
            return;
        }
        buf.writeVarLong(descriptor.session());
        buf.writeUtf(descriptor.imagePath(), 256);
        buf.writeUtf(descriptor.sha256(), 64);
        buf.writeVarInt(descriptor.width());
        buf.writeVarInt(descriptor.height());
        buf.writeVarInt(descriptor.tileSize());
        buf.writeVarInt(descriptor.maxLevel());
    }

    public static SyncTacticalMapBackgroundMessage decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new SyncTacticalMapBackgroundMessage(
                TacticalMapTileService.Descriptor.EMPTY);
        }
        long session = buf.readVarLong();
        String imagePath = buf.readUtf(256);
        String sha256 = buf.readUtf(64);
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int tileSize = buf.readVarInt();
        int maxLevel = buf.readVarInt();
        if (session <= 0L || !sha256.matches("[0-9a-f]{64}")
            || tileSize != TacticalMapPyramidLayout.TILE_SIZE
            || maxLevel < 0 || maxLevel >= TacticalMapPyramidLayout.MAX_LEVELS) {
            throw new IllegalArgumentException("Invalid tactical map descriptor");
        }
        TacticalMapPyramidLayout layout = new TacticalMapPyramidLayout(width, height);
        if (layout.maxLevel() != maxLevel) {
            throw new IllegalArgumentException("Inconsistent tactical map descriptor");
        }
        return new SyncTacticalMapBackgroundMessage(
            new TacticalMapTileService.Descriptor(
                session, imagePath, sha256, width, height, tileSize, maxLevel));
    }

    public static void handle(SyncTacticalMapBackgroundMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                applyOnClient(message.descriptor);
            }
        });
        context.setPacketHandled(true);
    }

    private static void applyOnClient(TacticalMapTileService.Descriptor descriptor) {
        try {
            Class<?> type = Class.forName(CLIENT_CACHE_CLASS);
            Object cache = type.getMethod("get").invoke(null);
            type.getMethod("applyDescriptor", TacticalMapTileService.Descriptor.class)
                .invoke(cache, descriptor);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                "Unable to apply tactical map descriptor on client", error);
        }
    }

    public static void applyDescriptorOnClient(TacticalMapTileService.Descriptor descriptor) {
        applyOnClient(descriptor);
    }

    /**
     * 下发地图 descriptor。
     *
     * @return true 表示这次真的发出去了；同一 (session, sha256) 在该玩家的自愈周期内不会重复下发
     *         （订阅续订因此只续 TTL，不再触发 descriptor + 预览瓦片的重复下发）。
     */
    public static boolean sendDescriptorOnly(ServerPlayer player) {
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        if (player == null) {
            return false;
        }
        if (!TacticalMapTileService.get().shouldSendDescriptor(
                player.getUUID(), descriptor.session(), descriptor.sha256())) {
            return false;
        }
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncTacticalMapBackgroundMessage(descriptor));
        if (descriptor.present()) {
            com.example.espoints.ESPointsMod.LOGGER.info(
                "已向 {} 发送战术地图 descriptor session={} {}x{} preview={}",
                player.getGameProfile().getName(), descriptor.session(),
                descriptor.width(), descriptor.height(), descriptor.maxLevel());
        }
        return true;
    }

    /**
     * 登录/重连时下发 descriptor。
     * <p>这里**不再**主动入队预览瓦片：预览与首屏瓦片改为玩家真正打开地图后由视口推送或客户端显式请求，
     * 避免"一进服就灌 277 KB 大包"把上行打满、导致 KeepAlive 超时被踢。</p>
     */
    public static void sendToPlayer(ServerPlayer player) {
        sendDescriptorOnly(player);
    }

    public static void broadcastToAll() {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        if (server == null) {
            return;
        }
        // 逐个下发（带"同 descriptor 不重复"判断）；预览瓦片同样不在此处推送。
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!TacticalMapTileService.get().shouldSendDescriptor(
                    player.getUUID(), descriptor.session(), descriptor.sha256())) {
                continue;
            }
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncTacticalMapBackgroundMessage(descriptor));
        }
    }
}
