package com.example.espoints.tile;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.ModConfig;
import com.example.espoints.network.SyncTacticalMapTileMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.espetro.api.ActiveBattlefieldSnapshot;
import org.espetro.api.EspetroAPI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Progressive SHA-addressed server pyramid.
 *
 * <p>Preview, individual tile and full-manifest readiness are independent. A
 * request never joins the full pyramid build. All decode, resize, validation
 * and disk I/O stays on a bounded worker pool; the server tick only performs a
 * bounded fair queue drain and sends already encoded PNG bytes.</p>
 */
public final class TacticalMapTileService {
    public static final int MAX_ENCODED_TILE_BYTES = 2 * 1024 * 1024;
    static final String PYRAMID_CACHE_VERSION = "p3";
    static final String COMPLETE_MANIFEST = ".complete";
    private static final int WORK_QUEUE_CAPACITY = 512;
    private static final int MAX_SEND_ATTEMPTS_PER_TICK = 64;
    private static final long PREVIEW_RESEND_MILLIS = 8_000L;
    /** 同一 descriptor 在该周期内不重复下发（自愈兜底周期）。 */
    private static final long DESCRIPTOR_RESYNC_MILLIS = 30_000L;
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final TacticalMapTileService INSTANCE = new TacticalMapTileService();

    private final AtomicLong generationSequence = new AtomicLong();
    private final ExecutorService executor = new ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY),
        runnable -> {
            Thread thread = new Thread(runnable, "ESPoints-TacticalMapTile");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.AbortPolicy());
    private final InFlightTaskRegistry<TacticalMapTileKey, byte[]> inFlight =
        new InFlightTaskRegistry<>();
    private final WeightedLruCache<TacticalMapTileKey, byte[]> memory =
        new WeightedLruCache<>(32L * 1024L * 1024L, bytes -> bytes.length);
    private final TileTransferLimiter transferLimiter = new TileTransferLimiter();
    private final FairTileRequestQueue<TacticalMapTileKey> sendQueue =
        new FairTileRequestQueue<>();
    private final Map<TacticalMapTileKey, Set<UUID>> waiters = new ConcurrentHashMap<>();
    private final Map<UUID, ViewportHint> playerViewports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPreviewEnqueueAt = new ConcurrentHashMap<>();
    /**
     * 已成功推送给该玩家的瓦片（玩家 → session → 键集合）。
     * <p>客户端会周期性续订（TTL 120 tick）并重发视口，"服务器主动推送"因此极易重复发送同一张瓦片；
     * 这里记录已推送过的键，视口/预览推送直接跳过，避免把上行带宽打满。
     * 客户端仍可通过 {@code RequestTacticalMapTileMessage} 显式请求，那条路径不受此限制（可自愈）。</p>
     */
    private final Map<UUID, Map<Long, Set<TacticalMapTileKey>>> pushedTiles = new ConcurrentHashMap<>();
    /** 已下发的 descriptor 指纹（session:sha256）与时间，用于订阅续订时不再重复下发。 */
    private final Map<UUID, String> descriptorFingerprint = new ConcurrentHashMap<>();
    private final Map<UUID, Long> descriptorSentAt = new ConcurrentHashMap<>();
    private volatile ActiveState active;

    private TacticalMapTileService() {
    }

    public static TacticalMapTileService get() {
        return INSTANCE;
    }

    public synchronized void activate(ActiveBattlefieldSnapshot snapshot) {
        clearLocked();
        byte[] backgroundBytes = snapshot == null ? new byte[0] : snapshot.backgroundBytes();
        if (snapshot == null
            || snapshot.backgroundSha256().isBlank()
            || !snapshot.backgroundSha256().matches("[0-9a-f]{64}")
            || snapshot.backgroundWidth() <= 0
            || snapshot.backgroundHeight() <= 0
            || backgroundBytes.length == 0) {
            ESPointsMod.LOGGER.error(
                "战术地图未激活：缺少有效底图 (sha='{}' {}x{} bytes={})",
                snapshot == null ? "" : snapshot.backgroundSha256(),
                snapshot == null ? 0 : snapshot.backgroundWidth(),
                snapshot == null ? 0 : snapshot.backgroundHeight(),
                backgroundBytes.length);
            return;
        }
        TacticalMapPyramidLayout layout =
            new TacticalMapPyramidLayout(snapshot.backgroundWidth(), snapshot.backgroundHeight());
        long session = Math.max(1L, EspetroAPI.getTacticalMapStateSnapshot().battlefieldSession());
        Path root = FMLPaths.CONFIGDIR.get()
            .resolve("espoints").resolve("cache").resolve("tactical-map");
        Path mapDirectory = root.resolve(cacheDirectoryName(snapshot.backgroundSha256()));
        ActiveState state = new ActiveState(
            generationSequence.incrementAndGet(),
            new Descriptor(session, snapshot.backgroundImage(), snapshot.backgroundSha256(),
                layout.width(), layout.height(), TacticalMapPyramidLayout.TILE_SIZE,
                layout.maxLevel()),
            layout, mapDirectory, backgroundBytes);
        active = state;
        memory.setMaximumWeight(
            Math.max(8L, ModConfig.tacticalMapServerMemoryMiB.get()) * 1024L * 1024L);
        try {
            state.build = CompletableFuture.runAsync(() -> buildPyramid(state, root), executor);
        } catch (RuntimeException error) {
            state.failAll(error);
            ESPointsMod.LOGGER.error("战术地图构建队列已满", error);
            return;
        }
        ESPointsMod.LOGGER.info(
            "战术地图已激活: session={} {}x{} previewLevel={} cache={}",
            session, layout.width(), layout.height(), layout.maxLevel(), mapDirectory.getFileName());
    }

    public synchronized void clear() {
        clearLocked();
    }

    private void clearLocked() {
        ActiveState previous = active;
        active = null;
        if (previous != null) {
            previous.cancel();
        }
        inFlight.clear();
        memory.clear();
        transferLimiter.clear();
        sendQueue.clear();
        waiters.clear();
        playerViewports.clear();
        lastPreviewEnqueueAt.clear();
        pushedTiles.clear();
        descriptorFingerprint.clear();
        descriptorSentAt.clear();
    }

    public Descriptor descriptor() {
        ActiveState state = active;
        return state == null ? Descriptor.EMPTY : state.descriptor;
    }

    /**
     * descriptor 是否仍需下发。
     * <p>同一 (session, sha256) 在该玩家身上已下发过、且未超过 {@link #DESCRIPTOR_RESYNC_MILLIS} 时返回 false，
     * 于是"订阅续订"只续 TTL、不再重复下发 descriptor。超时后会允许一次重发作为自愈兜底
     * （descriptor 只有几百字节，代价可忽略；真正的大流量是预览/视口瓦片）。</p>
     */
    public boolean shouldSendDescriptor(UUID playerId, long session, String sha256) {
        if (playerId == null) {
            return true;
        }
        String fingerprint = session + ":" + (sha256 == null ? "" : sha256);
        long now = System.currentTimeMillis();
        String previous = descriptorFingerprint.get(playerId);
        Long sentAt = descriptorSentAt.get(playerId);
        if (fingerprint.equals(previous) && sentAt != null
            && now - sentAt < DESCRIPTOR_RESYNC_MILLIS) {
            return false;
        }
        descriptorFingerprint.put(playerId, fingerprint);
        descriptorSentAt.put(playerId, now);
        return true;
    }

    private boolean hasPushed(UUID playerId, long session, int level, int x, int y) {
        Map<Long, Set<TacticalMapTileKey>> perPlayer = pushedTiles.get(playerId);
        if (perPlayer == null) {
            return false;
        }
        Set<TacticalMapTileKey> keys = perPlayer.get(session);
        return keys != null && keys.contains(new TacticalMapTileKey(session, level, x, y));
    }

    private void markPushed(UUID playerId, TacticalMapTileKey key) {
        if (playerId == null || key == null) {
            return;
        }
        pushedTiles.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(key.session(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
    }

    /** Returns bytes once this tile alone is published, irrespective of full build state. */
    public CompletableFuture<byte[]> request(long session, int level, int x, int y) {
        ActiveState state = active;
        if (state == null || state.descriptor.session() != session
            || !state.layout.isValid(level, x, y)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid/stale tactical tile request"));
        }
        TacticalMapTileKey key = new TacticalMapTileKey(session, level, x, y);
        byte[] cached = memory.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Path> readiness = state.readiness(key);
        if (state.registerDemand(key)) {
            startDemandBuild(state, key);
        }
        return inFlight.getOrStart(key, () -> readiness
            .thenApplyAsync(path -> readPublishedTile(state, key, path), executor));
    }

    /** Adds a key-only player waiter to the fair bounded send queue. */
    public FairTileRequestQueue.OfferResult enqueue(
            UUID playerId, long session, int level, int x, int y) {
        ActiveState state = active;
        if (playerId == null || state == null
            || state.descriptor.session() != session
            || !state.layout.isValid(level, x, y)) {
            return FairTileRequestQueue.OfferResult.GLOBAL_FULL;
        }
        TacticalMapTileKey key = new TacticalMapTileKey(session, level, x, y);
        FairTileRequestQueue.OfferResult result = sendQueue.offer(playerId, key);
        if (result == FairTileRequestQueue.OfferResult.ACCEPTED
            || result == FairTileRequestQueue.OfferResult.DUPLICATE) {
            waiters.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
                .add(playerId);
            request(session, level, x, y);
        }
        return result;
    }

    public void updatePlayerViewport(UUID playerId, double minX, double minY,
                                     double maxX, double maxY,
                                     int screenWidth, int screenHeight) {
        if (playerId == null) {
            return;
        }
        playerViewports.put(playerId, new ViewportHint(
            clamp01(minX), clamp01(minY), clamp01(maxX), clamp01(maxY),
            Math.max(1, screenWidth), Math.max(1, screenHeight)));
        transferLimiter.grantFirstGlance(playerId, System.currentTimeMillis());
    }

    public FairTileRequestQueue.OfferResult enqueuePreviewOnce(UUID playerId) {
        ActiveState state = active;
        if (playerId == null || state == null) {
            return FairTileRequestQueue.OfferResult.GLOBAL_FULL;
        }
        long now = System.currentTimeMillis();
        Long previous = lastPreviewEnqueueAt.get(playerId);
        if (previous != null && now - previous < PREVIEW_RESEND_MILLIS) {
            return FairTileRequestQueue.OfferResult.DUPLICATE;
        }
        if (hasPushed(playerId, state.descriptor.session(),
                state.descriptor.maxLevel(), 0, 0)) {
            // 预览瓦片已成功推送过：不再重发（客户端需要时会显式请求）。
            return FairTileRequestQueue.OfferResult.DUPLICATE;
        }
        FairTileRequestQueue.OfferResult result = enqueue(
            playerId, state.descriptor.session(), state.descriptor.maxLevel(), 0, 0);
        if (result == FairTileRequestQueue.OfferResult.ACCEPTED
            || result == FairTileRequestQueue.OfferResult.DUPLICATE) {
            lastPreviewEnqueueAt.put(playerId, now);
        }
        return result;
    }

    public void enqueueViewport(UUID playerId) {
        ActiveState state = active;
        ViewportHint view = playerId == null ? null : playerViewports.get(playerId);
        if (playerId == null || state == null || view == null) {
            enqueuePreviewOnce(playerId);
            return;
        }
        TacticalMapPyramidLayout layout = state.layout;
        int target = layout.chooseLevel(
            Math.max(0.0D, view.maxX - view.minX),
            Math.max(0.0D, view.maxY - view.minY),
            view.screenWidth, view.screenHeight);
        int arrival = TacticalMapLodPlanner.arrivalLevel(layout, target);
        enqueuePreviewOnce(playerId);
        if (arrival > target) {
            enqueueTiles(playerId, state, layout.visibleTiles(
                arrival, view.minX, view.minY, view.maxX, view.maxY, 0));
        }
        enqueueTiles(playerId, state, layout.visibleTiles(
            target, view.minX, view.minY, view.maxX, view.maxY, 0));
    }

    private void enqueueTiles(UUID playerId, ActiveState state,
                              List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            if (hasPushed(playerId, state.descriptor.session(),
                    tile.level(), tile.x(), tile.y())) {
                // 已推送给该玩家的瓦片不再重复入队（订阅续订会频繁走到这里）。
                continue;
            }
            enqueue(playerId, state.descriptor.session(),
                tile.level(), tile.x(), tile.y());
        }
    }

    /** Bounded server-tick drain. Not-ready/rate-limited keys rotate behind peers. */
    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int attempts = Math.min(MAX_SEND_ATTEMPTS_PER_TICK, sendQueue.size());
        for (int index = 0; index < attempts; index++) {
            FairTileRequestQueue.Entry<TacticalMapTileKey> entry =
                sendQueue.poll(this::pickSendKey);
            if (entry == null) {
                return;
            }
            TacticalMapTileKey key = entry.key();
            ActiveState state = active;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());
            if (state == null || state.descriptor.session() != key.session()
                || player == null) {
                removeWaiter(entry.playerId(), key);
                continue;
            }

            CompletableFuture<byte[]> future = request(
                key.session(), key.level(), key.x(), key.y());
            if (!future.isDone()) {
                sendQueue.defer(entry);
                continue;
            }
            byte[] bytes;
            try {
                bytes = future.join();
            } catch (CompletionException | CancellationException error) {
                // Drop only this waiter. The client scheduler retries with bounded backoff.
                removeWaiter(entry.playerId(), key);
                continue;
            }
            if (!allowTransfer(entry.playerId(), bytes.length)) {
                sendQueue.defer(entry);
                continue;
            }
            SyncTacticalMapTileMessage.sendToPlayer(
                player, key.session(), key.level(), key.x(), key.y(), bytes);
            markPushed(entry.playerId(), key);
            if (key.level() == state.descriptor.maxLevel() && key.x() == 0 && key.y() == 0) {
                ESPointsMod.LOGGER.info("发送战术地图预览瓦片 {} -> {} ({} bytes)",
                    key, player.getGameProfile().getName(), bytes.length);
            } else {
                ESPointsMod.LOGGER.debug("发送战术地图瓦片 {} -> {} ({} bytes)",
                    key, player.getGameProfile().getName(), bytes.length);
            }
            removeWaiter(entry.playerId(), key);
        }
    }

    /** Disconnect/unsubscribe removes one player's waiters, never shared build ownership. */
    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        sendQueue.removePlayer(playerId);
        transferLimiter.removePlayer(playerId);
        playerViewports.remove(playerId);
        lastPreviewEnqueueAt.remove(playerId);
        pushedTiles.remove(playerId);
        descriptorFingerprint.remove(playerId);
        descriptorSentAt.remove(playerId);
        for (Map.Entry<TacticalMapTileKey, Set<UUID>> entry : waiters.entrySet()) {
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                waiters.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public boolean allowTransfer(UUID playerId, int bytes) {
        long playerBudget = ModConfig.tacticalMapPlayerTransferKiBps.get() * 1024L;
        long globalBudget = ModConfig.tacticalMapGlobalTransferKiBps.get() * 1024L;
        return transferLimiter.allow(
            playerId, bytes, System.currentTimeMillis(), playerBudget, globalBudget);
    }

    public int tileWidth(int level, int x) {
        ActiveState state = active;
        return state == null ? 0 : state.layout.tileWidth(level, x);
    }

    public int tileHeight(int level, int y) {
        ActiveState state = active;
        return state == null ? 0 : state.layout.tileHeight(level, y);
    }

    int pendingSendCount() {
        return sendQueue.size();
    }

    int waiterCount(TacticalMapTileKey key) {
        Set<UUID> values = waiters.get(key);
        return values == null ? 0 : values.size();
    }

    private void removeWaiter(UUID playerId, TacticalMapTileKey key) {
        Set<UUID> values = waiters.get(key);
        if (values != null) {
            values.remove(playerId);
            if (values.isEmpty()) {
                waiters.remove(key, values);
            }
        }
    }

    private byte[] readPublishedTile(ActiveState state, TacticalMapTileKey key, Path path) {
        if (!isCurrent(state)) {
            throw new IllegalStateException("Stale tactical map session");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            validateEncodedBytes(bytes);
            if (!isCurrent(state)) {
                throw new IOException("Tactical map generation changed while reading");
            }
            synchronized (this) {
                if (!isCurrent(state)) {
                    throw new IOException("Tactical map generation changed before caching");
                }
                memory.put(key, bytes);
            }
            return bytes;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read tactical tile", error);
        }
    }

    private void buildPyramid(ActiveState state, Path root) {
        BufferedImage source = null;
        boolean sourcePublished = false;
        try {
            Files.createDirectories(state.mapDirectory);
            if (loadCompleteCache(state)) {
                state.finishCacheCheck(true);
                state.sourceBytes = new byte[0];
                touchAndPrune(state, root);
                state.manifestReady.complete(state.mapDirectory.resolve(COMPLETE_MANIFEST));
                ESPointsMod.LOGGER.info("战术地图瓦片缓存命中: session={} tiles={} dir={}",
                    state.descriptor.session(), orderedKeys(state).size(),
                    state.mapDirectory.getFileName());
                return;
            }

            source = ImageIO.read(new ByteArrayInputStream(state.sourceBytes));
            state.sourceBytes = new byte[0];
            if (source == null
                || source.getWidth() != state.layout.width()
                || source.getHeight() != state.layout.height()) {
                throw new IOException("Decoded tactical map dimensions do not match descriptor");
            }
            state.publishSource(source);
            sourcePublished = true;
            writePreviewTile(state, source);
            for (TacticalMapTileKey demanded : state.finishCacheCheck(false)) {
                startDemandBuild(state, demanded);
            }

            // Coarsest preview first, then progressively sharper visible-capable levels.
            for (int level = state.layout.maxLevel(); level >= 0; level--) {
                requireCurrent(state);
                BufferedImage scaled = level == 0
                    ? source
                    : TacticalMapImageScaler.scale(source, state.layout.levelWidth(level),
                        state.layout.levelHeight(level));
                try {
                    writeLevel(state, scaled, level);
                } finally {
                    if (scaled != source) {
                        scaled.flush();
                    }
                }
            }
            requireAllTilesReady(state);
            publishManifest(state);
            touchAndPrune(state, root);
        } catch (Throwable error) {
            if (!state.cancelled) {
                ESPointsMod.LOGGER.error("战术地图瓦片金字塔生成失败: {}",
                    state.descriptor.imagePath(), error);
            }
            state.failAll(error);
            throw error instanceof RuntimeException runtime
                ? runtime : new IllegalStateException(error);
        } finally {
            state.sourceBytes = new byte[0];
            if (!sourcePublished && source != null) {
                source.flush();
            }
            state.releaseImagesWhenIdle();
        }
    }

    /**
     * Claims a requested tile before submitting it so the background sweep and
     * all players converge on the same readiness future and output path.
     */
    private void writePreviewTile(ActiveState state, BufferedImage source) throws IOException {
        int previewLevel = state.layout.maxLevel();
        TacticalMapTileKey preview = new TacticalMapTileKey(
            state.descriptor.session(), previewLevel, 0, 0);
        if (state.isReady(preview) || !state.claimOwner(preview)) {
            return;
        }
        try {
            writeOwnedTile(state, state.demandLevelImage(previewLevel, source), preview);
            ESPointsMod.LOGGER.info("战术地图预览瓦片已就绪: {}", preview);
        } finally {
            state.releaseOwner(preview);
        }
    }

    private void startDemandBuild(ActiveState state, TacticalMapTileKey key) {
        if (!isCurrent(state) || state.isReady(key) || !state.claimOwner(key)) {
            return;
        }
        try {
            executor.execute(() -> buildDemandTile(state, key));
        } catch (RuntimeException error) {
            state.releaseOwner(key);
            state.failAll(error);
        }
    }

    private void buildDemandTile(ActiveState state, TacticalMapTileKey key) {
        BufferedImage source = null;
        try {
            requireCurrent(state);
            source = state.acquireSource();
            BufferedImage levelImage = state.demandLevelImage(key.level(), source);
            writeOwnedTile(state, levelImage, key);
        } catch (Throwable error) {
            // An owner must always complete (success or exceptional); otherwise
            // a manifest waiter could block forever after the owner disappears.
            state.failAll(error);
            if (!state.cancelled) {
                ESPointsMod.LOGGER.error("按需战术地图瓦片生成失败: {}", key, error);
            }
        } finally {
            if (source != null) {
                state.releaseSource();
            }
            state.releaseOwner(key);
        }
    }

    private boolean loadCompleteCache(ActiveState state) throws IOException {
        Path manifest = state.mapDirectory.resolve(COMPLETE_MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            return false;
        }
        String expected = manifestText(state);
        if (!expected.equals(Files.readString(manifest, StandardCharsets.UTF_8))) {
            Files.deleteIfExists(manifest);
            return false;
        }
        for (TacticalMapTileKey key : orderedKeys(state)) {
            requireCurrent(state);
            if (!state.claimOwner(key)) {
                throw new IOException("Duplicate tactical cache validator: " + key);
            }
            Path tile = tilePath(state.mapDirectory, key.level(), key.x(), key.y());
            try {
                try {
                    validateTileFile(state, key, tile);
                } catch (IOException corrupt) {
                    Files.deleteIfExists(manifest);
                    Files.deleteIfExists(tile);
                    return false;
                }
                state.publishReady(key, tile);
            } finally {
                state.releaseOwner(key);
            }
        }
        return true;
    }

    private void writeLevel(ActiveState state, BufferedImage image, int level)
            throws IOException {
        for (int y = 0; y < state.layout.rows(level); y++) {
            for (int x = 0; x < state.layout.columns(level); x++) {
                requireCurrent(state);
                TacticalMapTileKey key = new TacticalMapTileKey(
                    state.descriptor.session(), level, x, y);
                if (state.isReady(key)) {
                    continue;
                }
                if (!state.claimOwner(key)) {
                    // A demand task owns it and will publish the same future.
                    continue;
                }
                try {
                    writeOwnedTile(state, image, key);
                } finally {
                    state.releaseOwner(key);
                }
            }
        }
    }

    private void writeOwnedTile(ActiveState state, BufferedImage levelImage,
                                TacticalMapTileKey key) throws IOException {
        requireCurrent(state);
        Path target = tilePath(
            state.mapDirectory, key.level(), key.x(), key.y());
        Files.createDirectories(target.getParent());
        if (Files.isRegularFile(target)) {
            try {
                validateTileFile(state, key, target);
                requireCurrent(state);
                state.publishReady(key, target);
                return;
            } catch (IOException corrupt) {
                // The unique owner below atomically replaces this one bad tile.
            }
        }

        int width = state.layout.tileWidth(key.level(), key.x());
        int height = state.layout.tileHeight(key.level(), key.y());
        BufferedImage tile = levelImage.getSubimage(
            key.x() * TacticalMapPyramidLayout.TILE_SIZE,
            key.y() * TacticalMapPyramidLayout.TILE_SIZE, width, height);
        Path temporary = uniqueTemporary(target, state.generation);
        try {
            if (!ImageIO.write(tile, "PNG", temporary.toFile())) {
                throw new IOException("No PNG writer available");
            }
            validateTileFile(state, key, temporary);
            publishFileIfCurrent(state, temporary, target);
            validateTileFile(state, key, target);
            requireCurrent(state);
            state.publishReady(key, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void publishManifest(ActiveState state) throws IOException {
        Path manifest = state.mapDirectory.resolve(COMPLETE_MANIFEST);
        Path temporary = uniqueTemporary(manifest, state.generation);
        try {
            Files.writeString(temporary, manifestText(state), StandardCharsets.UTF_8);
            publishFileIfCurrent(state, temporary, manifest);
            if (!manifestText(state).equals(Files.readString(manifest, StandardCharsets.UTF_8))) {
                throw new IOException("Published tactical map manifest is inconsistent");
            }
            requireCurrent(state);
            state.manifestReady.complete(manifest);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private synchronized void publishFileIfCurrent(
            ActiveState state, Path temporary, Path target) throws IOException {
        requireCurrent(state);
        try {
            Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireAllTilesReady(ActiveState state) throws IOException {
        for (TacticalMapTileKey key : orderedKeys(state)) {
            CompletableFuture<Path> ready = state.readiness(key);
            if (!ready.isDone() && state.claimOwner(key)) {
                try {
                    BufferedImage source = state.acquireSource();
                    try {
                        writeOwnedTile(
                            state, state.demandLevelImage(key.level(), source), key);
                    } finally {
                        state.releaseSource();
                    }
                } finally {
                    state.releaseOwner(key);
                }
            }
            try {
                validateTileFile(state, key, ready.join());
            } catch (CompletionException error) {
                throw new IOException(
                    "Tile failed before manifest publication: " + key, error);
            }
        }
    }

    private static void validateTileFile(
            ActiveState state, TacticalMapTileKey key, Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing tactical map tile: " + path);
        }
        long size = Files.size(path);
        if (size < PNG_SIGNATURE.length || size > MAX_ENCODED_TILE_BYTES) {
            throw new IOException("Encoded tactical tile size is invalid");
        }
        byte[] signature = new byte[PNG_SIGNATURE.length];
        try (var input = Files.newInputStream(path)) {
            if (input.read(signature) != signature.length) {
                throw new IOException("Truncated tactical map PNG");
            }
        }
        validateEncodedBytes(signature);
        BufferedImage decoded = ImageIO.read(path.toFile());
        if (decoded == null
            || decoded.getWidth() != state.layout.tileWidth(key.level(), key.x())
            || decoded.getHeight() != state.layout.tileHeight(key.level(), key.y())) {
            if (decoded != null) {
                decoded.flush();
            }
            throw new IOException("Tactical map PNG dimensions are invalid");
        }
        decoded.flush();
    }

    static void validateEncodedBytes(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length
            || bytes.length > MAX_ENCODED_TILE_BYTES) {
            throw new IOException("Encoded tactical tile size is invalid");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                throw new IOException("Encoded tactical tile is not a PNG");
            }
        }
    }

    private static List<TacticalMapTileKey> orderedKeys(ActiveState state) {
        List<TacticalMapTileKey> keys = new ArrayList<>();
        for (int level = state.layout.maxLevel(); level >= 0; level--) {
            for (int y = 0; y < state.layout.rows(level); y++) {
                for (int x = 0; x < state.layout.columns(level); x++) {
                    keys.add(new TacticalMapTileKey(
                        state.descriptor.session(), level, x, y));
                }
            }
        }
        return keys;
    }

    private static String manifestText(ActiveState state) {
        StringBuilder text = new StringBuilder(256);
        text.append("version=").append(PYRAMID_CACHE_VERSION).append('\n')
            .append("sha256=").append(state.descriptor.sha256()).append('\n')
            .append("width=").append(state.layout.width()).append('\n')
            .append("height=").append(state.layout.height()).append('\n')
            .append("tileSize=").append(TacticalMapPyramidLayout.TILE_SIZE).append('\n')
            .append("maxLevel=").append(state.layout.maxLevel()).append('\n');
        List<TacticalMapTileKey> keys = orderedKeys(state);
        text.append("tiles=").append(keys.size()).append('\n');
        for (TacticalMapTileKey key : keys) {
            text.append(key.level()).append(',').append(key.x()).append(',').append(key.y())
                .append(',').append(state.layout.tileWidth(key.level(), key.x()))
                .append(',').append(state.layout.tileHeight(key.level(), key.y())).append('\n');
        }
        return text.toString();
    }

    static String cacheDirectoryName(String sha256) {
        return sha256 + "-" + PYRAMID_CACHE_VERSION;
    }

    public static Path localPreviewFile(String sha256, int maxLevel) {
        return localTileFile(sha256, maxLevel, 0, 0);
    }

    public static Path localTileFile(String sha256, int level, int x, int y) {
        return FMLPaths.CONFIGDIR.get()
            .resolve("espoints").resolve("cache").resolve("tactical-map")
            .resolve(cacheDirectoryName(sha256))
            .resolve("l" + level)
            .resolve(x + "_" + y + ".png");
    }

    private static Path tilePath(Path mapDirectory, int level, int x, int y) {
        return mapDirectory.resolve("l" + level).resolve(x + "_" + y + ".png");
    }

    private static Path uniqueTemporary(Path target, long generation) {
        return target.resolveSibling(target.getFileName() + ".tmp."
            + generation + "." + UUID.randomUUID());
    }

    private boolean isCurrent(ActiveState state) {
        return active == state && !state.cancelled && !state.failed;
    }

    private void requireCurrent(ActiveState state) throws IOException {
        if (!isCurrent(state)) {
            throw new IOException("Stale tactical map generation");
        }
    }

    private static void touchAndPrune(ActiveState state, Path root) throws IOException {
        Files.setLastModifiedTime(
            state.mapDirectory, FileTime.fromMillis(System.currentTimeMillis()));
        pruneDiskCache(root,
            ModConfig.tacticalMapDiskCacheMiB.get() * 1024L * 1024L,
            state.mapDirectory);
    }

    private static void pruneDiskCache(Path root, long budget, Path keep) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        long total = 0L;
        for (Path file : files) {
            total += Files.size(file);
        }
        if (total <= budget) {
            return;
        }
        List<Path> directories;
        try (var stream = Files.list(root)) {
            directories = stream.filter(Files::isDirectory)
                .filter(path -> !path.equals(keep))
                .sorted(Comparator.comparingLong(TacticalMapTileService::lastModified))
                .toList();
        }
        for (Path directory : directories) {
            if (total <= budget) {
                break;
            }
            long removed = directorySize(directory);
            deleteDirectory(directory);
            total -= removed;
        }
    }

    private static long directorySize(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException ignored) {
                    return 0L;
                }
            }).sum();
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = new ArrayList<>(stream.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private TacticalMapTileKey pickSendKey(
            UUID playerId, Set<TacticalMapTileKey> pending) {
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        int coarsest = Integer.MIN_VALUE;
        for (TacticalMapTileKey key : pending) {
            coarsest = Math.max(coarsest, key.level());
        }
        java.util.ArrayList<TacticalMapTileKey> same = new java.util.ArrayList<>();
        for (TacticalMapTileKey key : pending) {
            if (key.level() == coarsest) {
                same.add(key);
            }
        }
        if (same.size() == 1) {
            return same.get(0);
        }
        ViewportHint view = playerViewports.get(playerId);
        ActiveState state = active;
        if (view == null || state == null) {
            return same.get(0);
        }
        try {
            var tree = com.hexagram2021.tetrachordlib.core.container.KDTree
                .<TacticalMapTileKey, Double>newLinkedKDTree(2);
            for (TacticalMapTileKey key : same) {
                double[] center = tileCenter(state.layout, key);
                tree.insert(com.hexagram2021.tetrachordlib.core.container.KDTree.BuildNode.of(
                    key, new com.hexagram2021.tetrachordlib.core.container.impl.DoublePosition(
                        center[0], center[1])));
            }
            var closest = tree.findClosest(
                new com.hexagram2021.tetrachordlib.core.container.impl.DoublePosition(
                    (view.minX + view.maxX) * 0.5D,
                    (view.minY + view.maxY) * 0.5D));
            return closest == null ? same.get(0) : closest.other();
        } catch (RuntimeException ignored) {
            return same.get(0);
        }
    }

    private static double[] tileCenter(TacticalMapPyramidLayout layout,
                                       TacticalMapTileKey key) {
        double width = layout.levelWidth(key.level());
        double height = layout.levelHeight(key.level());
        return new double[] {
            (key.x() * (double) TacticalMapPyramidLayout.TILE_SIZE
                + layout.tileWidth(key.level(), key.x()) * 0.5D) / width,
            (key.y() * (double) TacticalMapPyramidLayout.TILE_SIZE
                + layout.tileHeight(key.level(), key.y()) * 0.5D) / height
        };
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record ViewportHint(double minX, double minY, double maxX, double maxY,
                                int screenWidth, int screenHeight) {
    }

    public record Descriptor(long session, String imagePath, String sha256,
                             int width, int height, int tileSize, int maxLevel) {
        public static final Descriptor EMPTY =
            new Descriptor(0L, "", "", 0, 0, TacticalMapPyramidLayout.TILE_SIZE, 0);

        public boolean present() {
            return session > 0L && !sha256.isBlank() && width > 0 && height > 0;
        }
    }

    private static final class ActiveState {
        private final long generation;
        private final Descriptor descriptor;
        private final TacticalMapPyramidLayout layout;
        private final Path mapDirectory;
        private final ProgressiveTileReadiness<TacticalMapTileKey, Path> readiness;
        private final CompletableFuture<Path> manifestReady = new CompletableFuture<>();
        private final Set<TacticalMapTileKey> demandOrder = new LinkedHashSet<>();
        private final Map<Integer, CompletableFuture<BufferedImage>> demandLevelImages =
            new ConcurrentHashMap<>();
        private volatile byte[] sourceBytes;
        private volatile boolean cancelled;
        private volatile boolean failed;
        private boolean cacheCheckComplete;
        private boolean cacheHit;
        private BufferedImage sourceImage;
        private int sourceUsers;
        private boolean releaseImagesRequested;
        private CompletableFuture<Void> build = CompletableFuture.completedFuture(null);

        private ActiveState(long generation, Descriptor descriptor,
                            TacticalMapPyramidLayout layout, Path mapDirectory,
                            byte[] sourceBytes) {
            this.generation = generation;
            this.descriptor = descriptor;
            this.layout = layout;
            this.mapDirectory = mapDirectory;
            this.sourceBytes = sourceBytes == null ? new byte[0] : sourceBytes;
            this.readiness = new ProgressiveTileReadiness<>(generation);
        }

        private CompletableFuture<Path> readiness(TacticalMapTileKey key) {
            return readiness.future(key);
        }

        private synchronized boolean registerDemand(TacticalMapTileKey key) {
            if (cancelled || failed || readiness.isReady(key) || !demandOrder.add(key)) {
                return false;
            }
            return cacheCheckComplete && !cacheHit;
        }

        private synchronized List<TacticalMapTileKey> finishCacheCheck(boolean hit) {
            cacheCheckComplete = true;
            cacheHit = hit;
            return hit ? List.of() : List.copyOf(demandOrder);
        }

        private synchronized void publishSource(BufferedImage source) {
            if (cancelled || failed || source == null || releaseImagesRequested) {
                throw new IllegalStateException("Cannot publish tactical map source");
            }
            sourceImage = source;
        }

        private synchronized BufferedImage acquireSource() {
            if (cancelled || failed || releaseImagesRequested || sourceImage == null) {
                throw new IllegalStateException("Tactical map source is unavailable");
            }
            sourceUsers++;
            return sourceImage;
        }

        private synchronized void releaseSource() {
            if (sourceUsers <= 0) {
                throw new IllegalStateException("Unbalanced tactical map source release");
            }
            sourceUsers--;
            releaseImagesIfIdle();
        }

        private BufferedImage demandLevelImage(int level, BufferedImage source) {
            if (level == 0) {
                return source;
            }
            CompletableFuture<BufferedImage> created = new CompletableFuture<>();
            CompletableFuture<BufferedImage> existing =
                demandLevelImages.putIfAbsent(level, created);
            CompletableFuture<BufferedImage> selected = existing == null ? created : existing;
            if (existing == null) {
                try {
                    created.complete(TacticalMapImageScaler.scale(
                        source, layout.levelWidth(level), layout.levelHeight(level)));
                } catch (Throwable error) {
                    created.completeExceptionally(error);
                }
            }
            return selected.join();
        }

        private synchronized void releaseImagesWhenIdle() {
            releaseImagesRequested = true;
            releaseImagesIfIdle();
        }

        private void releaseImagesIfIdle() {
            if (!releaseImagesRequested || sourceUsers != 0) {
                return;
            }
            if (sourceImage != null) {
                sourceImage.flush();
                sourceImage = null;
            }
            for (CompletableFuture<BufferedImage> imageFuture : demandLevelImages.values()) {
                if (imageFuture.isDone() && !imageFuture.isCompletedExceptionally()) {
                    imageFuture.join().flush();
                }
            }
            demandLevelImages.clear();
        }

        private void publishReady(TacticalMapTileKey key, Path path) {
            if (!cancelled && !failed && readiness.publish(generation, key, path)) {
                return;
            }
            if (!cancelled && !failed) {
                throw new IllegalStateException("Tile readiness was not owned: " + key);
            }
            throw new IllegalStateException("Tactical map generation is no longer active");
        }

        private boolean claimOwner(TacticalMapTileKey key) {
            return readiness.claim(generation, key);
        }

        private void releaseOwner(TacticalMapTileKey key) {
            readiness.release(key);
        }

        private boolean isReady(TacticalMapTileKey key) {
            return readiness.isReady(key);
        }

        private void cancel() {
            cancelled = true;
            build.cancel(true);
            failAll(new IllegalStateException("Tactical map generation cancelled"));
            sourceBytes = new byte[0];
            releaseImagesWhenIdle();
        }

        private void failAll(Throwable error) {
            failed = true;
            readiness.fail(error);
            manifestReady.completeExceptionally(error);
        }
    }
}
