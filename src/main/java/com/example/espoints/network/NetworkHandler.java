package com.example.espoints.network;

import com.example.espoints.ESPointsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络处理器类
 */
public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "16";
    
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );
    
    private static int packetId = 0;
    
    /**
     * 获取下一个包ID
     * @return 包ID
     */
    private static int nextPacketId() {
        return packetId++;
    }
    
    /**
     * 注册网络消息
     */
    public static void registerMessages() {
        INSTANCE.messageBuilder(SyncFriendlyVehiclesMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncFriendlyVehiclesMessage::encode).decoder(SyncFriendlyVehiclesMessage::decode)
            .consumerMainThread(SyncFriendlyVehiclesMessage::handle).add();
        INSTANCE.messageBuilder(SyncCapturePointsMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCapturePointsMessage::encode)
            .decoder(SyncCapturePointsMessage::decode)
            .consumerMainThread(SyncCapturePointsMessage::handle)
            .add();
        
        INSTANCE.messageBuilder(SyncConfigMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncConfigMessage::encode)
            .decoder(SyncConfigMessage::decode)
            .consumerMainThread(SyncConfigMessage::handle)
            .add();
        
        INSTANCE.messageBuilder(ShowMessagePopupMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ShowMessagePopupMessage::encode)
            .decoder(ShowMessagePopupMessage::decode)
            .consumerMainThread(ShowMessagePopupMessage::handle)
            .add();
        
        INSTANCE.messageBuilder(SyncOperationModeMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncOperationModeMessage::encode)
            .decoder(SyncOperationModeMessage::decode)
            .consumerMainThread(SyncOperationModeMessage::handle)
            .add();
        
        INSTANCE.messageBuilder(SyncPlayerPositionsMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncPlayerPositionsMessage::encode)
            .decoder(SyncPlayerPositionsMessage::decode)
            .consumerMainThread(SyncPlayerPositionsMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncPlayerIdentityMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncPlayerIdentityMessage::encode)
            .decoder(SyncPlayerIdentityMessage::decode)
            .consumerMainThread(SyncPlayerIdentityMessage::handle)
            .add();
            
        INSTANCE.messageBuilder(PlayLowReinforcementAudioMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PlayLowReinforcementAudioMessage::encode)
            .decoder(PlayLowReinforcementAudioMessage::decode)
            .consumerMainThread(PlayLowReinforcementAudioMessage::handle)
            .add();
        
        INSTANCE.messageBuilder(SyncMapPlayerDisplayMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMapPlayerDisplayMessage::encode)
            .decoder(SyncMapPlayerDisplayMessage::decode)
            .consumerMainThread(SyncMapPlayerDisplayMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncTacticalMapConfigMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTacticalMapConfigMessage::encode)
            .decoder(SyncTacticalMapConfigMessage::decode)
            .consumerMainThread(SyncTacticalMapConfigMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncTacticalMapBackgroundMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTacticalMapBackgroundMessage::encode)
            .decoder(SyncTacticalMapBackgroundMessage::decode)
            .consumerMainThread(SyncTacticalMapBackgroundMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncBastionsMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncBastionsMessage::encode)
            .decoder(SyncBastionsMessage::decode)
            .consumerMainThread(SyncBastionsMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncCapturePointOverviewMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCapturePointOverviewMessage::encode)
            .decoder(SyncCapturePointOverviewMessage::decode)
            .consumerMainThread(SyncCapturePointOverviewMessage::handle)
            .add();

        INSTANCE.messageBuilder(RequestCapturePointOverviewMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestCapturePointOverviewMessage::encode)
            .decoder(RequestCapturePointOverviewMessage::decode)
            .consumerMainThread(RequestCapturePointOverviewMessage::handle)
            .add();

        INSTANCE.messageBuilder(PlaceTacticalMarkerMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(PlaceTacticalMarkerMessage::encode)
            .decoder(PlaceTacticalMarkerMessage::decode)
            .consumerMainThread(PlaceTacticalMarkerMessage::handle)
            .add();

        INSTANCE.messageBuilder(RequestTacticalMarkersMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestTacticalMarkersMessage::encode)
            .decoder(RequestTacticalMarkersMessage::decode)
            .consumerMainThread(RequestTacticalMarkersMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncTacticalMarkersMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTacticalMarkersMessage::encode)
            .decoder(SyncTacticalMarkersMessage::decode)
            .consumerMainThread(SyncTacticalMarkersMessage::handle)
            .add();

        INSTANCE.messageBuilder(TacticalMarkerDeltaMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TacticalMarkerDeltaMessage::encode)
            .decoder(TacticalMarkerDeltaMessage::decode)
            .consumerMainThread(TacticalMarkerDeltaMessage::handle)
            .add();

        INSTANCE.messageBuilder(RemoveTacticalMarkerMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RemoveTacticalMarkerMessage::encode)
            .decoder(RemoveTacticalMarkerMessage::decode)
            .consumerMainThread(RemoveTacticalMarkerMessage::handle)
            .add();

        INSTANCE.messageBuilder(OpenArtillerySupportMapMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenArtillerySupportMapMessage::encode)
            .decoder(OpenArtillerySupportMapMessage::decode)
            .consumerMainThread(OpenArtillerySupportMapMessage::handle)
            .add();

        INSTANCE.messageBuilder(SelectArtillerySupportTargetMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(SelectArtillerySupportTargetMessage::encode)
            .decoder(SelectArtillerySupportTargetMessage::decode)
            .consumerMainThread(SelectArtillerySupportTargetMessage::handle)
            .add();

        INSTANCE.messageBuilder(TacticalMapSubscriptionMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(TacticalMapSubscriptionMessage::encode)
            .decoder(TacticalMapSubscriptionMessage::decode)
            .consumerMainThread(TacticalMapSubscriptionMessage::handle)
            .add();

        INSTANCE.messageBuilder(RequestTacticalMapTileMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestTacticalMapTileMessage::encode)
            .decoder(RequestTacticalMapTileMessage::decode)
            .consumerMainThread(RequestTacticalMapTileMessage::handle)
            .add();

        INSTANCE.messageBuilder(SyncTacticalMapTileMessage.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTacticalMapTileMessage::encode)
            .decoder(SyncTacticalMapTileMessage::decode)
            .consumerMainThread(SyncTacticalMapTileMessage::handle)
            .add();
    }
}
