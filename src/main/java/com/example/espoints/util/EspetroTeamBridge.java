package com.example.espoints.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.lang.reflect.Method;
import java.util.Locale;
import org.espetro.api.EspetroAPI;

/**
 * Normalizes HCRpoints team logic to Espetro's canonical ATTACK/DEFEND sides.
 */
public final class EspetroTeamBridge {
    public static final String ATTACK = "ATTACK";
    public static final String DEFEND = "DEFEND";
    public static final String ATTACK_SCOREBOARD_TEAM = "espetro_attack";
    public static final String DEFEND_SCOREBOARD_TEAM = "espetro_defend";

    private static final String ESPETRO_CLASS_NAME = "org.espetro.Espetro";
    private static final String ESPETRO_API_CLASS_NAME = "org.espetro.api.EspetroAPI";
    private static final String ESPETRO_VOTE_MANAGER_CLASS_NAME = "org.espetro.team.VoteManager";
    private static final String ESPETRO_SQUAD_MANAGER_CLASS_NAME = "org.espetro.team.SquadManager";
    private static final String ESPETRO_CLIENT_GAME_STATE_CLASS_NAME = "org.espetro.client.gui.ClientGameState";
    private static final String ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME =
        "org.espetro.client.gui.ClientTacticalState";
    private static final int DEFAULT_TEAMMATE_COLOR = 0xFFFFFFFF;

    private EspetroTeamBridge() {
    }

    public static String getPlayerTeam(Player player) {
        if (player == null) {
            return null;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            return getServerPlayerTeam(serverPlayer);
        }

        // 玩家实体上的记分板队伍随换队立即更新，优先级高于客户端缓存。
        String scoreboardTeam = getScoreboardTeam(player.getTeam());
        if (scoreboardTeam != null) {
            return scoreboardTeam;
        }

        // ClientGameState only describes the local player. Reusing it for a remote
        // player without a scoreboard team incorrectly makes unassigned players
        // look like members of the local side.
        if (player.isLocalPlayer()) {
            String clientTeam = getClientPlayerTeam();
            if (clientTeam != null) {
                return clientTeam;
            }
        }
        return null;
    }

    public static String getServerPlayerTeam(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        // 服务端分发以实体当前记分板队伍为准，避免职业缓存换队后短暂滞后。
        String scoreboardTeam = getScoreboardTeam(player.getTeam());
        if (scoreboardTeam != null) {
            return scoreboardTeam;
        }

        String espetroTeam = getEspetroPlayerTeam(player);
        if (espetroTeam != null) {
            return espetroTeam;
        }
        return null;
    }

    public static String getScoreboardTeam(Team team) {
        if (team == null) {
            return null;
        }

        String canonical = canonicalizeTeamName(team.getName());
        if (canonical != null) {
            return canonical;
        }

        if (team instanceof PlayerTeam playerTeam) {
            return canonicalizeTeamName(playerTeam.getDisplayName().getString());
        }

        return null;
    }

    public static boolean isEspetroTeamPlayer(Player player) {
        return getPlayerTeam(player) != null;
    }

    /**
     * Whether a player is actively deployed and may be represented on the tactical map.
     * The server uses Espetro's authoritative API; the client-side fallback rejects
     * unassigned, dead and spectator/waiting entities.
     */
    public static boolean isPlayerVisibleOnTacticalMap(Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Boolean fromApi = invokeStaticBooleanIfPresent(
                ESPETRO_API_CLASS_NAME,
                "isPlayerVisibleOnTacticalMap",
                new Class<?>[] {ServerPlayer.class},
                serverPlayer
            );
            if (fromApi != null) {
                return fromApi;
            }
        }
        // 客户端无法直接读取 Espetro 的服务端 waitingPlayers。死亡重部署现为
        // ADVENTURE + BLINDNESS，因此失明也是统一等待态的客户端兜底标记。
        return player.isAlive()
            && !player.isSpectator()
            && !player.hasEffect(MobEffects.BLINDNESS)
            && getPlayerTeam(player) != null;
    }

    public static boolean isSameTeam(String left, String right) {
        String leftTeam = canonicalizeTeamName(left);
        String rightTeam = canonicalizeTeamName(right);
        return leftTeam != null && leftTeam.equals(rightTeam);
    }

    public static String canonicalizeTeamName(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        String stripped = stripMinecraftFormatting(teamName).trim();
        String normalized = stripped.toLowerCase(Locale.ROOT);
        if (ATTACK.equalsIgnoreCase(stripped)
                || ATTACK_SCOREBOARD_TEAM.equals(normalized)
                || "attacker".equals(normalized)) {
            return ATTACK;
        }
        if (DEFEND.equalsIgnoreCase(stripped)
                || DEFEND_SCOREBOARD_TEAM.equals(normalized)
                || "defender".equals(normalized)) {
            return DEFEND;
        }

        if (normalized.contains("attack")
                || normalized.contains("attacker")
                || stripped.contains("进攻")
                || stripped.contains("攻方")) {
            return ATTACK;
        }
        if (normalized.contains("defend")
                || normalized.contains("defender")
                || stripped.contains("防守")
                || stripped.contains("守方")) {
            return DEFEND;
        }

        return null;
    }

    public static String scoreboardTeamId(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (ATTACK.equals(team)) {
            return ATTACK_SCOREBOARD_TEAM;
        }
        if (DEFEND.equals(team)) {
            return DEFEND_SCOREBOARD_TEAM;
        }
        return "";
    }

    public static String roleForTeam(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (ATTACK.equals(team)) {
            return "attacker";
        }
        if (DEFEND.equals(team)) {
            return "defender";
        }
        return null;
    }

    public static String displayName(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (team == null) {
            return canonicalTeam == null ? "" : canonicalTeam;
        }
        try {
            return EspetroAPI.teamDisplayName(team);
        } catch (Throwable ignored) {
            // Fall through for older Espetro without the coupling API.
        }
        if (ATTACK.equals(team)) {
            return "进攻方";
        }
        if (DEFEND.equals(team)) {
            return "防守方";
        }
        return team;
    }

    /** 战术地图着色：金=指挥官 / 紫=本队队长 / 蓝=本队队员 / 白=同阵营其它。 */
    public static final int MAP_COLOR_COMMANDER = 0xFFFFC766;
    public static final int MAP_COLOR_SQUAD_LEADER = 0xFFD48CFF;
    public static final int MAP_COLOR_SQUAD_MEMBER = 0xFF67A7FF;
    public static final int MAP_COLOR_FRIENDLY = 0xFFFFFFFF;

    /**
     * 使用 Espetro 名牌规则为战术地图上的队友图标着色（依赖客户端小队缓存）。
     */
    public static int getMapPlayerColor(String playerName) {
        try {
            Class<?> tacticalStateClass = Class.forName(ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME);
            Method method = tacticalStateClass.getMethod("getNameColor", String.class);
            Object result = method.invoke(null, playerName);
            return result instanceof Number color ? color.intValue() : DEFAULT_TEAMMATE_COLOR;
        } catch (ReflectiveOperationException ignored) {
            return DEFAULT_TEAMMATE_COLOR;
        }
    }

    /**
     * 根据服务端同步的小队元数据着色（相对本地玩家小队）。
     * 优先于纯名字查找，避免 ClientTacticalState 未同步时全白。
     */
    public static int getMapPlayerColor(String playerName, int squadId, boolean squadLeader, boolean commander) {
        if (commander) {
            return MAP_COLOR_COMMANDER;
        }
        int mySquadId = getLocalSquadId();
        if (squadId >= 0 && squadId == mySquadId) {
            return squadLeader ? MAP_COLOR_SQUAD_LEADER : MAP_COLOR_SQUAD_MEMBER;
        }
        // 服务端元数据已确认对方属于另一小队时，不能再让客户端名牌缓存
        // 覆盖为紫/蓝色；不同小队的同阵营玩家始终显示白色。
        if (squadId >= 0 && mySquadId >= 0) {
            return MAP_COLOR_FRIENDLY;
        }
        // 客户端尚未得到本地小队信息时，回退 Espetro 的历史名牌规则。
        int byName = getMapPlayerColor(playerName);
        if (byName != DEFAULT_TEAMMATE_COLOR && byName != MAP_COLOR_FRIENDLY) {
            return byName;
        }
        return MAP_COLOR_FRIENDLY;
    }

    public static int getLocalSquadId() {
        try {
            Class<?> tacticalStateClass = Class.forName(ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME);
            Method method = tacticalStateClass.getMethod("getMySquadId");
            Object result = method.invoke(null);
            return result instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    /** 公开：是否小队长（服务端）。 */
    public static boolean isSquadLeaderPublic(ServerPlayer player) {
        return isSquadLeader(player);
    }

    /** 服务端权威权限：指挥官、小队长、火力组组长或合法载具座位（EspetroAPI.canPlacePing）。 */
    public static boolean canPlaceTacticalMarker(ServerPlayer player) {
        return player != null && EspetroAPI.canPlacePing(player);
    }

    /**
     * 客户端粗检：无服务端玩家对象时，用 UUID 反射 canPlacePing；失败则 true 交服务端裁决。
     */
    public static boolean canPlaceTacticalMarkerClientHint(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        if (getPlayerTeam(player) == null) {
            return false;
        }
        // 载具类型与座位由服务端最终判断；客户端只要正在乘坐就允许打开轮盘。
        if (player.isPassenger()) {
            return true;
        }
        try {
            Class<?> tacticalState = Class.forName(ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME);
            Object value = tacticalState
                .getMethod("canLocalPlayerPlacePing", String.class)
                .invoke(null, player.getName().getString());
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException ignored) {
            // 缓存尚未同步时允许打开，选择结果仍必须通过服务端权威校验。
            return true;
        }
    }

    /** 对外：是否指挥官（放置标点时写入 ownerCommander）。 */
    public static boolean isCommander(ServerPlayer player) {
        return player != null && isCommanderInternal(player);
    }

    /** 对外：Espetro 小队编号；无小队为 {@link com.example.espoints.tactical.TacticalMarker#NO_SQUAD}。 */
    public static int getPlayerSquadId(ServerPlayer player) {
        if (player == null) {
            return com.example.espoints.tactical.TacticalMarker.NO_SQUAD;
        }
        Integer fromApi = invokeStaticIntIfPresent(ESPETRO_API_CLASS_NAME, "getPlayerSquadId",
            new Class<?>[] {java.util.UUID.class}, player.getUUID());
        if (fromApi != null) {
            return fromApi;
        }
        try {
            Class<?> squadManagerClass = Class.forName(ESPETRO_SQUAD_MANAGER_CLASS_NAME);
            Object squadManager = squadManagerClass.getMethod("getInstance").invoke(null);
            Object result = squadManagerClass.getMethod("getPlayerSquadId", java.util.UUID.class)
                .invoke(squadManager, player.getUUID());
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return com.example.espoints.tactical.TacticalMarker.NO_SQUAD;
    }

    public static boolean submitArtillerySupportTarget(ServerPlayer player, double x, double z) {
        return submitCommanderSkillTarget(player, x, z);
    }

    public static boolean submitCommanderSkillTarget(ServerPlayer player, double x, double z) {
        if (player == null || !Double.isFinite(x) || !Double.isFinite(z)) {
            return false;
        }
        Boolean result = invokeStaticBooleanIfPresent(ESPETRO_API_CLASS_NAME, "submitCommanderSkillTarget",
            new Class<?>[] {ServerPlayer.class, double.class, double.class}, player, x, z);
        if (result != null) {
            return result;
        }
        return invokeStaticBoolean(ESPETRO_API_CLASS_NAME, "submitArtillerySupportTarget",
                new Class<?>[] {ServerPlayer.class, double.class, double.class}, player, x, z);
    }

    /** 徐进弹幕：把直线起点与终点交给 Espetro 复核并执行。 */
    public static boolean submitArtilleryBarrageTarget(ServerPlayer player,
                                                       double startX, double startZ,
                                                       double endX, double endZ) {
        if (player == null || !Double.isFinite(startX) || !Double.isFinite(startZ)
            || !Double.isFinite(endX) || !Double.isFinite(endZ)) {
            return false;
        }
        Boolean result = invokeStaticBooleanIfPresent(ESPETRO_API_CLASS_NAME,
            "submitCommanderBarrageTarget",
            new Class<?>[] {ServerPlayer.class, double.class, double.class, double.class, double.class},
            player, startX, startZ, endX, endZ);
        return result != null && result;
    }

    private static boolean isCommanderInternal(ServerPlayer player) {
        if (invokeStaticBoolean(ESPETRO_API_CLASS_NAME, "isCommander",
                new Class<?>[] {java.util.UUID.class}, player.getUUID())) {
            return true;
        }

        try {
            Class<?> voteManagerClass = Class.forName(ESPETRO_VOTE_MANAGER_CLASS_NAME);
            Object voteManager = voteManagerClass.getMethod("getInstance").invoke(null);

            Method isCommanderMethod = voteManagerClass.getMethod("isCommander", java.util.UUID.class);
            Object directResult = isCommanderMethod.invoke(voteManager, player.getUUID());
            if (Boolean.TRUE.equals(directResult)) {
                return true;
            }

            Method getAttackCommander = voteManagerClass.getMethod("getAttackCommander");
            Method getDefendCommander = voteManagerClass.getMethod("getDefendCommander");
            return player.getUUID().equals(getAttackCommander.invoke(voteManager))
                || player.getUUID().equals(getDefendCommander.invoke(voteManager));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isSquadLeader(ServerPlayer player) {
        if (invokeStaticBoolean(ESPETRO_API_CLASS_NAME, "isSquadLeader",
                new Class<?>[] {java.util.UUID.class}, player.getUUID())) {
            return true;
        }

        try {
            Class<?> squadManagerClass = Class.forName(ESPETRO_SQUAD_MANAGER_CLASS_NAME);
            Object squadManager = squadManagerClass.getMethod("getInstance").invoke(null);
            Method getSquadSnapshots = squadManagerClass.getMethod("getSquadSnapshots", String.class);

            String team = getServerPlayerTeam(player);
            if (team != null && isSquadLeaderInSnapshots(
                    getSquadSnapshots.invoke(squadManager, team), player.getUUID())) {
                return true;
            }

            return isSquadLeaderInSnapshots(getSquadSnapshots.invoke(squadManager, ATTACK), player.getUUID())
                || isSquadLeaderInSnapshots(getSquadSnapshots.invoke(squadManager, DEFEND), player.getUUID());
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isSquadLeaderInSnapshots(Object snapshotsObject, java.util.UUID playerId)
        throws ReflectiveOperationException {
        if (!(snapshotsObject instanceof Iterable<?> snapshots)) {
            return false;
        }

        for (Object squad : snapshots) {
            Object membersObject = squad.getClass().getField("members").get(squad);
            if (!(membersObject instanceof Iterable<?> members)) {
                continue;
            }

            for (Object member : members) {
                Object memberId = member.getClass().getField("uuid").get(member);
                Object leader = member.getClass().getField("leader").get(member);
                if (playerId.equals(memberId) && Boolean.TRUE.equals(leader)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean invokeStaticBoolean(String className, String methodName,
                                               Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Boolean invokeStaticBooleanIfPresent(String className, String methodName,
                                                        Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Integer invokeStaticIntIfPresent(String className, String methodName,
                                                    Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, parameterTypes);
            Object result = method.invoke(null, args);
            return result instanceof Number number ? number.intValue() : null;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getEspetroPlayerTeam(ServerPlayer player) {
        try {
            Class<?> espetroClass = Class.forName(ESPETRO_CLASS_NAME);
            Method method = espetroClass.getMethod("getPlayerTeam", ServerPlayer.class);
            Object result = method.invoke(null, player);
            return result instanceof String team ? canonicalizeTeamName(team) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getClientPlayerTeam() {
        try {
            Class<?> clientGameStateClass = Class.forName(ESPETRO_CLIENT_GAME_STATE_CLASS_NAME);
            Method method = clientGameStateClass.getMethod("getPlayerTeam");
            Object result = method.invoke(null);
            return result instanceof String team ? canonicalizeTeamName(team) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stripMinecraftFormatting(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean skipNext = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (current == '\u00a7' && i + 1 < value.length()) {
                skipNext = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
