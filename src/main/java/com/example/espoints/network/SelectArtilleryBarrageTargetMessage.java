package com.example.espoints.network;

import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.espetro.api.EspetroAPI;

import java.util.function.Supplier;

/**
 * 客户端在 155 火炮支援地图上拖出直线（徐进弹幕），提交起点与终点。
 * 服务端校验地图边界后交给 Espetro 复核指挥官权限与冷却。
 */
public class SelectArtilleryBarrageTargetMessage {

    private final double startX;
    private final double startZ;
    private final double endX;
    private final double endZ;

    public SelectArtilleryBarrageTargetMessage(double startX, double startZ, double endX, double endZ) {
        this.startX = startX;
        this.startZ = startZ;
        this.endX = endX;
        this.endZ = endZ;
    }

    public static void encode(SelectArtilleryBarrageTargetMessage message, FriendlyByteBuf buf) {
        buf.writeDouble(message.startX);
        buf.writeDouble(message.startZ);
        buf.writeDouble(message.endX);
        buf.writeDouble(message.endZ);
    }

    public static SelectArtilleryBarrageTargetMessage decode(FriendlyByteBuf buf) {
        return new SelectArtilleryBarrageTargetMessage(
            PacketValidation.checkedCoordinate(buf.readDouble(), "barrage start x"),
            PacketValidation.checkedCoordinate(buf.readDouble(), "barrage start z"),
            PacketValidation.checkedCoordinate(buf.readDouble(), "barrage end x"),
            PacketValidation.checkedCoordinate(buf.readDouble(), "barrage end z"));
    }

    public static void handle(SelectArtilleryBarrageTargetMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                handleServer(sender, message);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleServer(ServerPlayer sender, SelectArtilleryBarrageTargetMessage message) {
        if (!EspetroAPI.isActiveBattlefield(sender.serverLevel())) {
            sender.sendSystemMessage(Component.literal("§c请进入当前战场后再选择目标。"));
            return;
        }
        TacticalMapJsonConfig.TacticalMapBounds bounds = TacticalMapJsonConfig.getInstance().getBounds();
        if (!bounds.contains(message.startX, message.startZ)
            || !bounds.contains(message.endX, message.endZ)) {
            sender.sendSystemMessage(Component.literal("§c徐进弹幕坐标超出战术地图允许范围。"));
            return;
        }
        if (!EspetroTeamBridge.submitArtilleryBarrageTarget(sender,
                message.startX, message.startZ, message.endX, message.endZ)) {
            sender.sendSystemMessage(Component.literal("§c徐进弹幕坐标提交失败。"));
        }
    }
}
