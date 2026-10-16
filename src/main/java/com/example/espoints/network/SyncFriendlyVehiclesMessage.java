package com.example.espoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Only the recipient's team is sent. Types/names are bounded; no entity references cross threads. */
public record SyncFriendlyVehiclesMessage(String dimension, String team, List<Vehicle> vehicles) {
    public static final int MAX_VEHICLES = 256;
    /** 编制 JSON map_icon 的文件名长度上限（正常都远小于此值）。 */
    public static final int MAX_ICON_LENGTH = 64;
    public record Vehicle(UUID id, String type, String name, double x, double z, float yaw,
                          int passengers, int squadId, boolean loaded, String mapIcon) {
        public Vehicle {
            if (id == null || type == null || name == null || type.length() > 128 || name.length() > 128
                || !Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || passengers < 0 || passengers > 256 || squadId < -1
                || (mapIcon != null && mapIcon.length() > MAX_ICON_LENGTH))
                throw new IllegalArgumentException("Invalid vehicle");
        }
        /** 兼容不携带地图图标的旧调用点（map_icon 缺失 = 客户端回退车型关键字表）。 */
        public Vehicle(UUID id, String type, String name, double x, double z, float yaw,
                       int passengers, int squadId, boolean loaded) {
            this(id, type, name, x, z, yaw, passengers, squadId, loaded, null);
        }
    }
    public SyncFriendlyVehiclesMessage {
        if (dimension == null || dimension.length() > 256 || team == null
            || !(team.equals("ATTACK") || team.equals("DEFEND")) || vehicles.size() > MAX_VEHICLES)
            throw new IllegalArgumentException("Invalid vehicle frame");
        vehicles = List.copyOf(vehicles);
    }
    public static void encode(SyncFriendlyVehiclesMessage message, FriendlyByteBuf buf) {
        buf.writeUtf(message.dimension, 256); buf.writeUtf(message.team, 16); buf.writeVarInt(message.vehicles.size());
        for (var v : message.vehicles) {
            buf.writeUUID(v.id()); buf.writeUtf(v.type(), 128); buf.writeUtf(v.name(), 128);
            buf.writeDouble(v.x()); buf.writeDouble(v.z()); buf.writeFloat(v.yaw());
            buf.writeVarInt(v.passengers()); buf.writeVarInt(v.squadId()); buf.writeBoolean(v.loaded());
            boolean hasMapIcon = v.mapIcon() != null && !v.mapIcon().isEmpty();
            buf.writeBoolean(hasMapIcon);
            if (hasMapIcon) buf.writeUtf(v.mapIcon(), MAX_ICON_LENGTH);
        }
    }
    public static SyncFriendlyVehiclesMessage decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(256);
        String team = buf.readUtf(16);
        int count = PacketValidation.checkedCount(buf.readVarInt(), MAX_VEHICLES, "friendly vehicle");
        var vehicles = new ArrayList<Vehicle>();
        var ids = new java.util.HashSet<UUID>();
        for (int i = 0; i < count; i++) {
            var v = new Vehicle(buf.readUUID(), buf.readUtf(128), buf.readUtf(128), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean() ? buf.readUtf(MAX_ICON_LENGTH) : null);
            if (!ids.add(v.id())) throw new IllegalArgumentException("Duplicate vehicle");
            vehicles.add(v);
        }
        return new SyncFriendlyVehiclesMessage(dimension, team, vehicles);
    }
    public static void handle(SyncFriendlyVehiclesMessage message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.example.espoints.hud.TacticalMapHUD.getInstance().syncFriendlyVehicles(message));
        context.setPacketHandled(true);
    }
}
