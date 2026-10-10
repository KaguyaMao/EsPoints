package com.example.espoints.tactical;

import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

public final class FriendlyVehicleIcons {
    private FriendlyVehicleIcons() { }
    public static String fileFor(String type) {
        String name = type.toLowerCase(Locale.ROOT);
        if (has(name, "logistic", "supply", "ural", "truck")) return "map_truck_logistics.png";
        if (has(name, "heli", "helicopter", "uh60", "mi8", "z19", "apache")) return "map_transporthelo.png";
        if (has(name, "uav", "drone")) return "map_uav.png";
        if (has(name, "boat", "ship")) return "map_boat.png";
        if (has(name, "antiair", "anti_air", "pantsir", "shilka")) return "map_antiair.png";
        if (has(name, "ifv", "bmp", "bradley", "lav", "zbd")) return "map_ifv.png";
        if (has(name, "apc", "btr", "m113", "stryker")) return "map_apc.png";
        if (has(name, "tank", "mbt", "abrams", "leopard", "t90", "t72", "t_90", "t_72", "ztz", "challenger")) return "map_tank.png";
        return "map_jeep.png";
    }
    public static ResourceLocation texture(String type) {
        return ResourceLocation.fromNamespaceAndPath("espoints", "textures/gui/mark/friendly/" + fileFor(type));
    }
    private static boolean has(String name, String... fragments) {
        for (String fragment : fragments) if (name.contains(fragment)) return true;
        return false;
    }
}
