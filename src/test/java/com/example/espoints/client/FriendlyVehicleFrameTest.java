package com.example.espoints.client;

import com.example.espoints.network.SyncFriendlyVehiclesMessage;
import com.example.espoints.tactical.FriendlyVehicleIcons;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FriendlyVehicleFrameTest {
    @Test void vehicleFrameRoundTripsTeamDimensionAndLiveState() {
        var frame=new SyncFriendlyVehiclesMessage("minecraft:test","ATTACK",List.of(
            new SyncFriendlyVehiclesMessage.Vehicle(UUID.randomUUID(),"sbw:t90","坦克",12.5,-42.5,135,3,2,true)));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try { SyncFriendlyVehiclesMessage.encode(frame,buf);assertEquals(frame,SyncFriendlyVehiclesMessage.decode(buf)); }
        finally {buf.release();}
    }
    @Test void rejectsUnboundedOrNonFiniteData() {
        assertThrows(IllegalArgumentException.class,()->new SyncFriendlyVehiclesMessage("x","unknown",List.of()));
        assertThrows(IllegalArgumentException.class,()->new SyncFriendlyVehiclesMessage.Vehicle(UUID.randomUUID(),"tank","x",Double.NaN,0,0,0,-1,true));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {buf.writeUtf("minecraft:test");buf.writeUtf("ATTACK");buf.writeVarInt(257);
            assertThrows(IllegalArgumentException.class,()->SyncFriendlyVehiclesMessage.decode(buf));}
        finally {buf.release();}
    }
    @Test void friendlyIconSelectionCoversCommonTypesAndHasSafeFallback() {
        assertEquals("map_tank.png",FriendlyVehicleIcons.fileFor("sbw:t90"));
        assertEquals("map_ifv.png",FriendlyVehicleIcons.fileFor("sbw:bmp2"));
        assertEquals("map_truck_logistics.png",FriendlyVehicleIcons.fileFor("sbw:logistics_truck"));
        assertEquals("map_jeep.png",FriendlyVehicleIcons.fileFor("custom:unknown"));
    }
}
