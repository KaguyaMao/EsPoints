package com.example.espoints.tactical;

import com.example.espoints.ESPointsMod;
import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.SyncFriendlyVehiclesMessage;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.espetro.api.EspetroAPI;
import org.espetro.vehicle.VehicleManager;
import java.util.HashMap;
import java.util.List;

@Mod.EventBusSubscriber(modid = ESPointsMod.MOD_ID)
public final class FriendlyVehicleMapSync {
    private FriendlyVehicleMapSync() { }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        var server = ESPointsMod.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % 10 != 0) return;
        var level = EspetroAPI.getActiveBattlefieldLevel(server).orElse(null);
        if (level == null) return;
        var frames = new HashMap<String, List<SyncFriendlyVehiclesMessage.Vehicle>>();
        var snapshots = VehicleManager.getInstance().tacticalVehicleSnapshots(level);
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != level || !CapturePointManager.getInstance()
                    .isTacticalMapSubscribed(player, server.getTickCount())) continue;
            String team = EspetroTeamBridge.canonicalizeTeamName(EspetroTeamBridge.getServerPlayerTeam(player));
            if (team == null) continue;
            var frame = frames.computeIfAbsent(team, key -> snapshots.stream()
                .filter(v -> key.equals(EspetroTeamBridge.canonicalizeTeamName(v.team())))
                .limit(SyncFriendlyVehiclesMessage.MAX_VEHICLES)
                .map(v -> new SyncFriendlyVehiclesMessage.Vehicle(v.id(), bounded(v.type()), bounded(v.name()), v.x(), v.z(),
                    v.yaw(), v.passengers(), v.squadId(), v.loaded())).toList());
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncFriendlyVehiclesMessage(level.dimension().location().toString(), team, frame));
        }
    }
    private static String bounded(String text) { return text == null ? "" : text.substring(0, Math.min(128, text.length())); }
}
