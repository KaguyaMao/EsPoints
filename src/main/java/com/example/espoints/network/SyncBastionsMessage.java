package com.example.espoints.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 同步可见兵站信息的网络消息。
 */
public class SyncBastionsMessage {
    private static final int MAX_BASTIONS = 256;
    private static final int MAX_BASES = 64;
    private static final int MAX_SUPPLY_STATIONS = 128;
    private static final int MAX_FIXED_WEAPONS = 256;
    private static final String TACTICAL_MAP_HUD_CLASS = "com.example.espoints.hud.TacticalMapHUD";
    private final List<BastionInfo> bastions;
    private final List<BaseInfo> bases;
    private final List<VehicleSupplyStationInfo> vehicleSupplyStations;
    private final List<FixedWeaponInfo> fixedWeapons;

    public SyncBastionsMessage(List<BastionInfo> bastions) {
        this(bastions, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases) {
        this(bastions, bases, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases,
                               List<VehicleSupplyStationInfo> vehicleSupplyStations) {
        this(bastions, bases, vehicleSupplyStations, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases,
                               List<VehicleSupplyStationInfo> vehicleSupplyStations,
                               List<FixedWeaponInfo> fixedWeapons) {
        this.bastions = List.copyOf(bastions);
        this.bases = List.copyOf(bases);
        this.vehicleSupplyStations = List.copyOf(vehicleSupplyStations);
        this.fixedWeapons = List.copyOf(fixedWeapons);
    }

    public static class BastionInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;
        private final String type;
        private final int construction;
        private final int ammunition;
        private final boolean operational;
        private final double buildRadius;
        private final double exclusionRadius;
        private final long nextWaveAtMillis;

        public BastionInfo(String name, String team, BlockPos pos) {
            this(name, team, pos, "FOB", 0, 0, true, 150.0, 400.0, 0L);
        }

        public BastionInfo(String name, String team, BlockPos pos, String type,
                           int construction, int ammunition, boolean operational,
                           double buildRadius, double exclusionRadius, long nextWaveAtMillis) {
            this.name = name;
            this.team = team;
            this.pos = pos;
            this.type = type == null ? "FOB" : type;
            this.construction = Math.max(0, construction);
            this.ammunition = Math.max(0, ammunition);
            this.operational = operational;
            this.buildRadius = Math.max(0.0, buildRadius);
            this.exclusionRadius = Math.max(0.0, exclusionRadius);
            this.nextWaveAtMillis = Math.max(0L, nextWaveAtMillis);
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getType() {
            return type;
        }

        public boolean isRally() {
            return "RALLY".equalsIgnoreCase(type);
        }

        public boolean isRadio() {
            return "RADIO".equalsIgnoreCase(type) || "FOB".equalsIgnoreCase(type);
        }

        public boolean isHab() {
            return "HAB".equalsIgnoreCase(type);
        }

        public int getConstruction() {
            return construction;
        }

        public int getAmmunition() {
            return ammunition;
        }

        public boolean isOperational() {
            return operational;
        }

        public double getBuildRadius() {
            return buildRadius;
        }

        public double getExclusionRadius() {
            return exclusionRadius;
        }

        public long getNextWaveSeconds() {
            return Math.max(0L,
                (nextWaveAtMillis - System.currentTimeMillis() + 999L) / 1000L);
        }

        public long getNextWaveAtMillis() {
            return nextWaveAtMillis;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof BastionInfo other)) return false;
            return construction == other.construction
                && ammunition == other.ammunition
                && operational == other.operational
                && Double.compare(buildRadius, other.buildRadius) == 0
                && Double.compare(exclusionRadius, other.exclusionRadius) == 0
                && nextWaveAtMillis == other.nextWaveAtMillis
                && name.equals(other.name) && team.equals(other.team)
                && pos.equals(other.pos) && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos, type, construction, ammunition,
                operational, buildRadius, exclusionRadius, nextWaveAtMillis);
        }
    }

    public static class BaseInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;
        private final float yaw;

        public BaseInfo(String name, String team, BlockPos pos, float yaw) {
            this.name = name;
            this.team = team;
            this.pos = pos;
            this.yaw = yaw;
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        public float getYaw() {
            return yaw;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof BaseInfo other)) return false;
            return Float.compare(yaw, other.yaw) == 0
                && name.equals(other.name) && team.equals(other.team) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos, yaw);
        }
    }

    public static class VehicleSupplyStationInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;

        public VehicleSupplyStationInfo(String name, String team, BlockPos pos) {
            this.name = name;
            this.team = team;
            this.pos = pos;
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof VehicleSupplyStationInfo other)) return false;
            return name.equals(other.name) && team.equals(other.team) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos);
        }
    }

    /** 战术地图上的固定武器（已启用 fixed_weapon 的工事）。 */
    public static class FixedWeaponInfo {
        private final String id;
        private final String team;
        private final BlockPos pos;
        private final String weaponTypeId;
        private final String mapIcon;

        public FixedWeaponInfo(String id, String team, BlockPos pos,
                               String weaponTypeId, String mapIcon) {
            this.id = id == null ? "" : id;
            this.team = team;
            this.pos = pos;
            this.weaponTypeId = weaponTypeId == null ? "" : weaponTypeId;
            String icon = mapIcon == null ? "" : mapIcon.trim();
            this.mapIcon = icon.isEmpty() ? null : icon;
        }

        public String getId() {
            return id;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getWeaponTypeId() {
            return weaponTypeId;
        }

        /** 编制 {@code map_icon} 给的文件名；null = 交给客户端默认映射。 */
        public String getMapIcon() {
            return mapIcon;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof FixedWeaponInfo other)) return false;
            return id.equals(other.id) && team.equals(other.team) && pos.equals(other.pos)
                && weaponTypeId.equals(other.weaponTypeId)
                && Objects.equals(mapIcon, other.mapIcon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, team, pos, weaponTypeId, mapIcon);
        }
    }

    public static void encode(SyncBastionsMessage msg, FriendlyByteBuf buf) {
        PacketValidation.checkedCount(msg.bastions.size(), MAX_BASTIONS, "bastion");
        PacketValidation.checkedCount(msg.bases.size(), MAX_BASES, "base");
        PacketValidation.checkedCount(
            msg.vehicleSupplyStations.size(), MAX_SUPPLY_STATIONS, "supply station");
        buf.writeVarInt(msg.bastions.size());
        for (BastionInfo bastion : msg.bastions) {
            validateBastion(bastion);
            buf.writeUtf(bastion.getName(), 64);
            buf.writeUtf(bastion.getTeam(), 32);
            buf.writeBlockPos(bastion.getPos());
            buf.writeUtf(bastion.getType(), 16);
            buf.writeVarInt(bastion.getConstruction());
            buf.writeVarInt(bastion.getAmmunition());
            buf.writeBoolean(bastion.isOperational());
            buf.writeDouble(bastion.getBuildRadius());
            buf.writeDouble(bastion.getExclusionRadius());
            buf.writeVarLong(bastion.getNextWaveAtMillis());
        }

        buf.writeVarInt(msg.bases.size());
        for (BaseInfo base : msg.bases) {
            validateCommon(base.getName(), base.getTeam(), base.getPos());
            if (!Float.isFinite(base.getYaw())) {
                throw new IllegalArgumentException("Invalid base yaw");
            }
            buf.writeUtf(base.getName(), 64);
            buf.writeUtf(base.getTeam(), 32);
            buf.writeBlockPos(base.getPos());
            buf.writeFloat(base.getYaw());
        }

        buf.writeVarInt(msg.vehicleSupplyStations.size());
        for (VehicleSupplyStationInfo station : msg.vehicleSupplyStations) {
            validateCommon(station.getName(), station.getTeam(), station.getPos());
            buf.writeUtf(station.getName(), 64);
            buf.writeUtf(station.getTeam(), 32);
            buf.writeBlockPos(station.getPos());
        }

        PacketValidation.checkedCount(msg.fixedWeapons.size(), MAX_FIXED_WEAPONS, "fixed weapon");
        buf.writeVarInt(msg.fixedWeapons.size());
        for (FixedWeaponInfo weapon : msg.fixedWeapons) {
            validateFixedWeapon(weapon);
            buf.writeUtf(weapon.getId(), 128);
            buf.writeUtf(weapon.getTeam(), 32);
            buf.writeBlockPos(weapon.getPos());
            buf.writeUtf(weapon.getWeaponTypeId(), 128);
            buf.writeUtf(weapon.getMapIcon() == null ? "" : weapon.getMapIcon(), 64);
        }
    }

    public static SyncBastionsMessage decode(FriendlyByteBuf buf) {
        int size = PacketValidation.checkedCount(
            buf.readVarInt(), MAX_BASTIONS, "bastion");
        List<BastionInfo> bastions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf(64);
            String team = buf.readUtf(32);
            BlockPos pos = buf.readBlockPos();
            String type = buf.readUtf(16);
            int construction = buf.readVarInt();
            int ammunition = buf.readVarInt();
            boolean operational = buf.readBoolean();
            double buildRadius = buf.readDouble();
            double exclusionRadius = buf.readDouble();
            long nextWaveAtMillis = buf.readVarLong();
            if (construction < 0 || ammunition < 0
                || buildRadius < 0.0D || exclusionRadius < 0.0D
                || nextWaveAtMillis < 0L) {
                throw new IllegalArgumentException("Negative bastion field");
            }
            BastionInfo bastion = new BastionInfo(name, team, pos, type,
                construction, ammunition, operational, buildRadius,
                exclusionRadius, nextWaveAtMillis);
            validateBastion(bastion);
            bastions.add(bastion);
        }

        int baseSize = PacketValidation.checkedCount(
            buf.readVarInt(), MAX_BASES, "base");
        List<BaseInfo> bases = new ArrayList<>(baseSize);
        for (int i = 0; i < baseSize; i++) {
            String name = buf.readUtf(64);
            String team = buf.readUtf(32);
            BlockPos pos = buf.readBlockPos();
            float yaw = buf.readFloat();
            validateCommon(name, team, pos);
            if (!Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Invalid base yaw");
            }
            bases.add(new BaseInfo(name, team, pos, yaw));
        }

        int stationSize = PacketValidation.checkedCount(
            buf.readVarInt(), MAX_SUPPLY_STATIONS, "vehicle supply station");
        List<VehicleSupplyStationInfo> stations = new ArrayList<>(stationSize);
        for (int i = 0; i < stationSize; i++) {
            String name = buf.readUtf(64);
            String team = buf.readUtf(32);
            BlockPos pos = buf.readBlockPos();
            validateCommon(name, team, pos);
            stations.add(new VehicleSupplyStationInfo(name, team, pos));
        }

        int weaponSize = PacketValidation.checkedCount(
            buf.readVarInt(), MAX_FIXED_WEAPONS, "fixed weapon");
        List<FixedWeaponInfo> fixedWeapons = new ArrayList<>(weaponSize);
        for (int i = 0; i < weaponSize; i++) {
            String id = buf.readUtf(128);
            String team = buf.readUtf(32);
            BlockPos pos = buf.readBlockPos();
            String weaponTypeId = buf.readUtf(128);
            String mapIcon = buf.readUtf(64);
            FixedWeaponInfo weapon = new FixedWeaponInfo(id, team, pos, weaponTypeId, mapIcon);
            validateFixedWeapon(weapon);
            fixedWeapons.add(weapon);
        }
        return new SyncBastionsMessage(bastions, bases, stations, fixedWeapons);
    }

    private static void validateBastion(BastionInfo bastion) {
        validateCommon(bastion.getName(), bastion.getTeam(), bastion.getPos());
        if (bastion.getType() == null || bastion.getType().isBlank()
            || bastion.getType().length() > 16
            || bastion.getConstruction() > 10_000_000
            || bastion.getAmmunition() > 10_000_000
            || !Double.isFinite(bastion.getBuildRadius())
            || !Double.isFinite(bastion.getExclusionRadius())
            || bastion.getBuildRadius() > 100_000.0D
            || bastion.getExclusionRadius() > 100_000.0D
            || bastion.getNextWaveAtMillis() < 0L) {
            throw new IllegalArgumentException("Invalid bastion payload");
        }
    }

    private static void validateFixedWeapon(FixedWeaponInfo weapon) {
        if (weapon.getId() == null || weapon.getId().isBlank() || weapon.getId().length() > 128
            || weapon.getTeam() == null || weapon.getTeam().length() > 32
            || weapon.getPos() == null
            || weapon.getWeaponTypeId() == null || weapon.getWeaponTypeId().length() > 128) {
            throw new IllegalArgumentException("Invalid fixed weapon payload");
        }
        String icon = weapon.getMapIcon();
        if (icon != null && icon.length() > 64) {
            throw new IllegalArgumentException("Invalid fixed weapon map icon");
        }
    }

    private static void validateCommon(String name, String team, BlockPos pos) {
        if (name == null || name.isBlank() || name.length() > 64
            || team == null || team.length() > 32 || pos == null) {
            throw new IllegalArgumentException("Invalid tactical structure payload");
        }
    }

    public static void handle(SyncBastionsMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient(msg.bastions, msg.bases, msg.vehicleSupplyStations,
                    msg.fixedWeapons);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleOnClient(List<BastionInfo> bastions, List<BaseInfo> bases,
                                       List<VehicleSupplyStationInfo> vehicleSupplyStations,
                                       List<FixedWeaponInfo> fixedWeapons) {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("syncBastionsFromServer", List.class, List.class, List.class,
                    List.class)
                .invoke(hud, bastions, bases, vehicleSupplyStations, fixedWeapons);
        } catch (ReflectiveOperationException e) {
            // 客户端 HUD 不可用时忽略兵站显示同步，避免影响主功能。
        }
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions) {
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBastionsMessage(bastions)
        );
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions, List<BaseInfo> bases) {
        sendToPlayer(player, bastions, bases, List.of());
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions, List<BaseInfo> bases,
                                    List<VehicleSupplyStationInfo> vehicleSupplyStations) {
        sendToPlayer(player, bastions, bases, vehicleSupplyStations, List.of());
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions, List<BaseInfo> bases,
                                    List<VehicleSupplyStationInfo> vehicleSupplyStations,
                                    List<FixedWeaponInfo> fixedWeapons) {
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBastionsMessage(bastions, bases, vehicleSupplyStations, fixedWeapons)
        );
    }
}
