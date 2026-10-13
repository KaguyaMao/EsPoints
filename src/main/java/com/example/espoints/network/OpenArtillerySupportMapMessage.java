package com.example.espoints.network;

import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端通知客户端打开155火炮支援选点地图。
 */
public class OpenArtillerySupportMapMessage {
    /** "point" = 右键点选；"line" = 按住右键画线（徐进弹幕）。 */
    private final String targetMode;

    public OpenArtillerySupportMapMessage() {
        this("point");
    }

    public OpenArtillerySupportMapMessage(String targetMode) {
        this.targetMode = "line".equalsIgnoreCase(targetMode) ? "line" : "point";
    }

    public String targetMode() {
        return targetMode;
    }

    public static void encode(OpenArtillerySupportMapMessage message, FriendlyByteBuf buf) {
        buf.writeUtf(message.targetMode, 8);
    }

    public static OpenArtillerySupportMapMessage decode(FriendlyByteBuf buf) {
        return new OpenArtillerySupportMapMessage(buf.readUtf(8));
    }

    public static void handle(OpenArtillerySupportMapMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                enterSelectionMode(message.targetMode);
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 不再打开独立的「火炮支援选点」页面：直接进入选点模式，
     * 指挥官就在已打开的 J 菜单战术地图上操作。
     */
    private static void enterSelectionMode(String targetMode) {
        try {
            Class<?> hudClass = Class.forName("com.example.espoints.hud.TacticalMapHUD");
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            try {
                hudClass.getMethod("beginArtillerySelection", String.class)
                    .invoke(hud, targetMode);
            } catch (NoSuchMethodException legacy) {
                hudClass.getMethod("beginArtillerySelection").invoke(hud);
            }
        } catch (ReflectiveOperationException e) {
            ModLogger.syncError("Failed to enter artillery selection mode: " + e.getMessage());
        }
    }

    public static void sendTo(ServerPlayer player) {
        sendTo(player, "point");
    }

    public static void sendTo(ServerPlayer player, String targetMode) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new OpenArtillerySupportMapMessage(targetMode));
    }
}
