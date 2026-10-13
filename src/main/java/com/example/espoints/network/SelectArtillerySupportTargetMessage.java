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
 * 客户端在155火炮支援地图中选择轰炸点。
 * 服务端会校验地图边界，并把坐标交给 Espetro 重新校验指挥官权限和冷却。
 */
public class SelectArtillerySupportTargetMessage {
    private final double x;
    private final double z;

    public SelectArtillerySupportTargetMessage(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public static void encode(SelectArtillerySupportTargetMessage message, FriendlyByteBuf buf) {
        buf.writeDouble(message.x);
        buf.writeDouble(message.z);
    }

    public static SelectArtillerySupportTargetMessage decode(FriendlyByteBuf buf) {
        return new SelectArtillerySupportTargetMessage(
            PacketValidation.checkedCoordinate(buf.readDouble(), "artillery x"),
            PacketValidation.checkedCoordinate(buf.readDouble(), "artillery z"));
    }

    public static void handle(SelectArtillerySupportTargetMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender == null) {
                return;
            }
            handleServer(sender, message.x, message.z);
        });
        context.setPacketHandled(true);
    }

    private static void handleServer(ServerPlayer sender, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        if (!EspetroAPI.isActiveBattlefield(sender.serverLevel())) {
            sender.sendSystemMessage(Component.literal("§c请进入当前战场后再选择目标。"));
            return;
        }

        TacticalMapJsonConfig.TacticalMapBounds bounds = TacticalMapJsonConfig.getInstance().getBounds();
        if (!bounds.contains(x, z)) {
            sender.sendSystemMessage(Component.literal("§c155火炮支援坐标超出战术地图允许范围。"));
            return;
        }

        if (!EspetroTeamBridge.submitArtillerySupportTarget(sender, x, z)) {
            sender.sendSystemMessage(Component.literal("§c155火炮支援坐标提交失败。"));
            return;
        }

        // 不再额外放置战术标点：炮击目标改由覆盖区（红色圆圈）表示，
        // 原来的 ARTILLERY_TARGET 标点在地图上会显示成一辆卡车图标，属于误导。
    }
}
