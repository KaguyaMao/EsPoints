package com.example.espoints.client;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.TacticalMapConfig;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.RequestTacticalMapTileMessage;
import com.example.espoints.tile.TacticalMapLodPlanner;
import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapTileAtlasLayout;
import com.example.espoints.tile.TacticalMapTileKey;
import com.example.espoints.tile.TacticalMapTileService;
import com.example.espoints.tile.TacticalMapTextureFilterPolicy;
import com.example.espoints.tile.ClientTileRequestScheduler;
import com.example.espoints.tile.RollingLatencyWindow;
import com.example.espoints.tile.WeightedLruCache;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background PNG decode + render-thread-only registration with a weighted LRU.
 * The coarsest tile is touched as the fallback on every render, so it is retained.
 */
public final class ClientTacticalMapTileCache {
    private static final int MAX_DECODED_UPLOAD_QUEUE = 24;
    private static final int MAX_QUEUED_DECODES = 32;
    private static final int DEFAULT_SENDS_PER_TICK = 8;
    private static final int MAX_LOCAL_LOADS_PER_UPDATE = 24;
    private static final long DESIRED_PLAN_EXPIRY_MILLIS = 15_000L;
    private static final ClientTacticalMapTileCache INSTANCE =
        new ClientTacticalMapTileCache();

    private final WeightedLruCache<TacticalMapTileKey, TextureEntry> textures =
        new WeightedLruCache<>(64L * 1024L * 1024L, TextureEntry::weight);
    private final ClientTileRequestScheduler<TacticalMapTileKey> requests =
        new ClientTileRequestScheduler<>();
    private final Set<TacticalMapTileKey> decoding = new HashSet<>();
    private static final Comparator<DecodeJob> DECODE_ORDER =
        Comparator.comparingInt(DecodeJob::priority)
            .thenComparingLong(DecodeJob::sequence);
    /** Includes only not-yet-started jobs; decoding set also includes two workers. */
    private final PriorityQueue<DecodeJob> pendingDecodes =
        new PriorityQueue<>(DECODE_ORDER);
    private final Map<TacticalMapTileKey, Integer> desiredRanks = new HashMap<>();
    /** Encoded PNG awaiting render-thread decode+upload; bounded to avoid a first-open burst. */
    private final Deque<PendingUpload> decodedUploadQueue = new ArrayDeque<>();
    private TacticalMapTileService.Descriptor descriptor =
        TacticalMapTileService.Descriptor.EMPTY;
    private TacticalMapPyramidLayout layout;
    private long generation;
    private long decodeSequence;
    private long lastDesiredPlanAt;
    private List<TacticalMapPyramidLayout.TileCoordinate> lastDesiredPlan = List.of();
    private long lastDesiredPlanRevision = Long.MIN_VALUE;
    private long readinessRevision;
    private TextureEntry layerAtlas;
    private AtlasKey layerAtlasKey;
    private final AtomicLong nativeImagesCreated = new AtomicLong();
    private final AtomicLong nativeImagesClosed = new AtomicLong();
    private final RollingLatencyWindow uploadLatencyNanos = new RollingLatencyWindow(256);

    private ClientTacticalMapTileCache() {
        for (int index = 0; index < 2; index++) {
            Thread worker = new Thread(
                this::runDecodeWorker, "ESPoints-TacticalMapDecode-" + (index + 1));
            worker.setDaemon(true);
            worker.start();
        }
    }

    public static ClientTacticalMapTileCache get() {
        return INSTANCE;
    }

    public synchronized void applyDescriptor(TacticalMapTileService.Descriptor incoming) {
        TacticalMapTileService.Descriptor normalized = incoming == null
            ? TacticalMapTileService.Descriptor.EMPTY : incoming;
        if (descriptor.session() == normalized.session()
            && descriptor.sha256().equals(normalized.sha256())) {
            // 同一份 descriptor：不再重复请求预览、也不重建纹理。
            // 之前这里会 request(preview) + tryLoadLocalPreview()，而客户端订阅续订会被服务端回一次
            // descriptor，于是同一张 277 KB 预览瓦片被反复请求/重传（实测一次会话 46 次 ≈12 MB），
            // 把上行占满导致 KeepAlive 超时被踢。
            return;
        }
        releaseAll();
        generation++;
        descriptor = normalized;
        layout = normalized.present()
            ? new TacticalMapPyramidLayout(normalized.width(), normalized.height())
            : null;
        for (TextureEntry evicted : textures.setMaximumWeight(
                Math.max(16L, TacticalMapConfig.tileTextureCacheMiB.get())
                    * 1024L * 1024L)) {
            release(evicted);
        }
        if (normalized.present()) {
            TacticalMapTileKey preview = new TacticalMapTileKey(
                normalized.session(), normalized.maxLevel(), 0, 0);
            requests.updateDesired(List.of(preview));
            desiredRanks.put(preview, 0);
            lastDesiredPlanAt = System.currentTimeMillis();
            ESPointsMod.LOGGER.info("客户端已应用战术地图 descriptor session={} {}x{} previewLevel={}",
                normalized.session(), normalized.width(), normalized.height(),
                normalized.maxLevel());
            tryLoadLocalPreview(normalized);
        } else {
            ESPointsMod.LOGGER.info("客户端战术地图 descriptor 已清空");
        }
    }

    public synchronized TacticalMapTileService.Descriptor descriptor() {
        return descriptor;
    }

    public synchronized TacticalMapPyramidLayout layout() {
        return layout;
    }

    public synchronized boolean hasAll(List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return true;
        }
        if (!descriptor.present()) {
            return false;
        }
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            if (tile == null || textures.get(new TacticalMapTileKey(
                    descriptor.session(), tile.level(), tile.x(), tile.y())) == null) {
                return false;
            }
        }
        return true;
    }

    public synchronized TextureEntry texture(int level, int x, int y) {
        if (!descriptor.present()) {
            return null;
        }
        return textures.get(new TacticalMapTileKey(descriptor.session(), level, x, y));
    }

    /**
     * One GPU texture covering a complete visible LOD rectangle. Rebuilds only
     * when the tile set or any constituent texture changes.
     */
    public synchronized LayerAtlas composeLayer(
            int level, List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        if (layout == null || !descriptor.present()) {
            return null;
        }
        TacticalMapTileAtlasLayout.Spec spec =
            TacticalMapTileAtlasLayout.spec(layout, level, tiles);
        if (spec == null) {
            return null;
        }
        AtlasKey key = new AtlasKey(
            descriptor.session(), generation, readinessRevision, spec);
        if (layerAtlas != null && key.equals(layerAtlasKey)) {
            return new LayerAtlas(layerAtlas, spec);
        }
        NativeImage composed;
        try {
            composed = stampLayer(spec, tiles);
        } catch (RuntimeException error) {
            ESPointsMod.LOGGER.warn("战术地图图层拼接失败: L{} {}", level, error.toString());
            return null;
        }
        if (composed == null) {
            return null;
        }
        nativeImagesCreated.incrementAndGet();
        try {
            DynamicTexture texture = new DynamicTexture(composed);
            TacticalMapTextureSampling.apply(texture, false);
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
                "espoints_tactical_layer_" + descriptor.session() + "_"
                    + level + "_" + readinessRevision, texture);
            TextureEntry entry = new TextureEntry(
                location, texture, composed.getWidth(), composed.getHeight());
            entry.linear = false;
            TextureEntry previous = layerAtlas;
            layerAtlas = entry;
            layerAtlasKey = key;
            if (previous != null) {
                release(previous);
            }
            return new LayerAtlas(entry, spec);
        } catch (RuntimeException error) {
            closeImage(composed);
            throw error;
        }
    }

    private NativeImage stampLayer(
            TacticalMapTileAtlasLayout.Spec spec,
            List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        NativeImage composed = new NativeImage(spec.width(), spec.height(), false);
        try {
            for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
                TextureEntry source = textures.get(new TacticalMapTileKey(
                    descriptor.session(), tile.level(), tile.x(), tile.y()));
                NativeImage pixels = source == null ? null : source.pixels();
                if (source == null || pixels == null
                    || source.width() != layout.tileWidth(tile.level(), tile.x())
                    || source.height() != layout.tileHeight(tile.level(), tile.y())
                    || pixels.getWidth() != source.width()
                    || pixels.getHeight() != source.height()) {
                    composed.close();
                    return null;
                }
                copyTile(composed, pixels,
                    TacticalMapTileAtlasLayout.atlasX(spec, tile.x()),
                    TacticalMapTileAtlasLayout.atlasY(spec, tile.y()));
            }
            return composed;
        } catch (RuntimeException error) {
            composed.close();
            throw error;
        }
    }

    private static void copyTile(NativeImage dest, NativeImage source, int destX, int destY) {
        int width = source.getWidth();
        int height = source.getHeight();
        for (int y = 0; y < height; y++) {
            int targetY = destY + y;
            if (targetY < 0 || targetY >= dest.getHeight()) {
                continue;
            }
            for (int x = 0; x < width; x++) {
                int targetX = destX + x;
                if (targetX < 0 || targetX >= dest.getWidth()) {
                    continue;
                }
                dest.setPixelRGBA(targetX, targetY, source.getPixelRGBA(x, y));
            }
        }
    }

    public synchronized TacticalMapLodPlanner.TileState tileState(
            TacticalMapPyramidLayout.TileCoordinate tile) {
        if (layout == null || tile == null
            || !layout.isValid(tile.level(), tile.x(), tile.y())) {
            return TacticalMapLodPlanner.TileState.MISSING;
        }
        TacticalMapTileKey key =
            new TacticalMapTileKey(descriptor.session(), tile.level(), tile.x(), tile.y());
        if (textures.get(key) != null) {
            return TacticalMapLodPlanner.TileState.READY;
        }
        if (decoding.contains(key)) {
            return TacticalMapLodPlanner.TileState.REQUESTED;
        }
        if (requests.isOutstanding(key) || requests.isDesired(key)) {
            return TacticalMapLodPlanner.TileState.REQUESTED;
        }
        return TacticalMapLodPlanner.TileState.MISSING;
    }

    public long textureBudgetBytes() {
        return Math.max(16L, TacticalMapConfig.tileTextureCacheMiB.get())
            * 1024L * 1024L;
    }

    public synchronized void requestCurrentPreview() {
        if (!descriptor.present() || layout == null) {
            return;
        }
        request(descriptor.maxLevel(), 0, 0);
    }

    public synchronized void request(int level, int x, int y) {
        if (layout == null || !layout.isValid(level, x, y)) {
            return;
        }
        TacticalMapTileKey key =
            new TacticalMapTileKey(descriptor.session(), level, x, y);
        if (textures.get(key) != null) {
            return;
        }
        requests.addDesired(key);
        desiredRanks.putIfAbsent(key, desiredRanks.size());
        lastDesiredPlanAt = System.currentTimeMillis();
    }

    /** Render publishes an immutable ordered desire snapshot; networking runs on tick. */
    public synchronized void updateDesired(
            List<TacticalMapPyramidLayout.TileCoordinate> orderedTiles) {
        if (layout == null || !descriptor.present()) {
            requests.clear();
            desiredRanks.clear();
            lastDesiredPlan = List.of();
            return;
        }
        List<TacticalMapPyramidLayout.TileCoordinate> normalizedPlan =
            orderedTiles == null ? List.of() : List.copyOf(orderedTiles);
        if (lastDesiredPlan.equals(normalizedPlan)
            && lastDesiredPlanRevision == readinessRevision) {
            lastDesiredPlanAt = System.currentTimeMillis();
            return;
        }
        java.util.ArrayList<TacticalMapTileKey> ordered = new java.util.ArrayList<>();
        TacticalMapTileKey preview = new TacticalMapTileKey(
            descriptor.session(), descriptor.maxLevel(), 0, 0);
        if (textures.get(preview) == null) {
            ordered.add(preview);
        }
        if (orderedTiles != null) {
            for (TacticalMapPyramidLayout.TileCoordinate tile : orderedTiles) {
                if (tile != null && layout.isValid(tile.level(), tile.x(), tile.y())) {
                    TacticalMapTileKey key = new TacticalMapTileKey(
                        descriptor.session(), tile.level(), tile.x(), tile.y());
                    if (textures.get(key) == null) {
                        ordered.add(key);
                    }
                }
            }
        }
        requests.updateDesired(ordered);
        desiredRanks.clear();
        for (int index = 0; index < ordered.size(); index++) {
            desiredRanks.putIfAbsent(ordered.get(index), index);
        }
        lastDesiredPlan = normalizedPlan;
        lastDesiredPlanRevision = readinessRevision;
        discardUndesiredQueuedWork();
        lastDesiredPlanAt = System.currentTimeMillis();
        tryLoadLocalTiles(ordered);
    }

    /** Called from client tick; sends preview/visible/ring in render-produced order. */
    public void tickRequests() {
        List<TacticalMapTileKey> toSend;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long textureBudget = textureBudgetBytes();
            boolean evicted = false;
            for (TextureEntry entry : textures.setMaximumWeight(textureBudget)) {
                release(entry);
                evicted = true;
            }
            if (evicted) {
                readinessRevision++;
            }
            if (lastDesiredPlanAt > 0L
                && now - lastDesiredPlanAt > DESIRED_PLAN_EXPIRY_MILLIS) {
                TacticalMapTileKey preview = previewKeyOrNull();
                requests.clear();
                desiredRanks.clear();
                lastDesiredPlan = List.of();
                lastDesiredPlanRevision = Long.MIN_VALUE;
                if (preview != null && textures.get(preview) == null) {
                    requests.addDesired(preview);
                    desiredRanks.put(preview, 0);
                    lastDesiredPlanAt = now;
                } else {
                    lastDesiredPlanAt = 0L;
                }
            }
            if (Minecraft.getInstance().getConnection() == null) {
                suspendRequests();
                return;
            }
            toSend = requests.poll(now, DEFAULT_SENDS_PER_TICK);
        }
        for (TacticalMapTileKey key : toSend) {
            NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMapTileMessage(
                key.session(), key.level(), key.x(), key.y()));
        }
    }

    public void accept(long session, int level, int x, int y,
                       int width, int height, byte[] encoded) {
        TacticalMapTileKey key = new TacticalMapTileKey(session, level, x, y);
        synchronized (this) {
            if (layout == null || descriptor.session() != session
                || !layout.isValid(level, x, y)
                || layout.tileWidth(level, x) != width
                || layout.tileHeight(level, y) != height) {
                ESPointsMod.LOGGER.warn(
                    "丢弃战术地图瓦片: session={} level={} ({},{}) {}x{} layout={} descriptorSession={}",
                    session, level, x, y, width, height,
                    layout == null ? "null" : (layout.width() + "x" + layout.height()),
                    descriptor.session());
                return;
            }
            boolean preview = isPreview(key);
            if (!preview && !requests.isDesired(key) && !requests.isOutstanding(key)
                && !requests.isProcessing(key)) {
                return;
            }
            if (preview) {
                requests.addDesired(key);
            }
            if (encoded == null || encoded.length < 8
                || encoded.length > TacticalMapTileService.MAX_ENCODED_TILE_BYTES) {
                requests.reject(key, System.currentTimeMillis());
                return;
            }
            requests.received(key);
            if (textures.get(key) != null) {
                requests.complete(key);
                desiredRanks.remove(key);
                return;
            }
            if (decoding.contains(key)) {
                return;
            }
            decoding.add(key);
            PendingUpload pending = new PendingUpload(
                key, generation, width, height, encoded);
            if (preview) {
                decodedUploadQueue.addFirst(pending);
            } else if (decodedUploadQueue.size() >= MAX_DECODED_UPLOAD_QUEUE) {
                PendingUpload evicted = decodedUploadQueue.pollLast();
                if (evicted != null) {
                    decoding.remove(evicted.key());
                    requests.reject(evicted.key(), System.currentTimeMillis());
                }
                decodedUploadQueue.addLast(pending);
            } else {
                decodedUploadQueue.addLast(pending);
            }
            ESPointsMod.LOGGER.info(
                "客户端已收到战术地图瓦片 {} {}x{} ({} bytes, queue={})",
                key, width, height, encoded.length, decodedUploadQueue.size());
        }
    }

    public synchronized void clear() {
        descriptor = TacticalMapTileService.Descriptor.EMPTY;
        layout = null;
        generation++;
        releaseAll();
    }

    /** Stops hidden-map work while retaining already uploaded texture cache entries. */
    public synchronized void suspendRequests() {
        // Do not bump generation: in-flight preview/decode/upload must still land.
        requests.clear();
        desiredRanks.clear();
        pendingDecodes.clear();
        decoding.clear();
        decodedUploadQueue.clear();
        lastDesiredPlanAt = 0L;
        lastDesiredPlan = List.of();
        lastDesiredPlanRevision = Long.MIN_VALUE;
    }

    /** Called once per client tick; texture creation is deliberately frame-budgeted. */
    public void drainUploadQueue(int maximumUploads, long softBudgetNanos) {
        int remaining = Math.max(0, maximumUploads);
        long startedAt = System.nanoTime();
        while (remaining-- > 0) {
            if (System.nanoTime() - startedAt >= Math.max(0L, softBudgetNanos)) {
                return;
            }
            PendingUpload pending;
            synchronized (this) {
                pending = decodedUploadQueue.pollFirst();
            }
            if (pending == null) return;
            NativeImage image;
            try {
                image = decode(pending.encoded(), pending.width(), pending.height());
            } catch (RuntimeException error) {
                markDecodeFailed(pending.key());
                ESPointsMod.LOGGER.warn("战术地图瓦片解码失败: {}", pending.key(), error);
                continue;
            }
            long uploadStartedAt = System.nanoTime();
            register(pending.key(), pending.generation(), image);
            uploadLatencyNanos.record(System.nanoTime() - uploadStartedAt);
        }
    }

    private NativeImage decode(byte[] encoded, int width, int height) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(encoded));
            nativeImagesCreated.incrementAndGet();
            if (image.getWidth() != width || image.getHeight() != height) {
                closeImage(image);
                throw new IOException("Decoded tile dimensions do not match packet");
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private synchronized void register(
            TacticalMapTileKey key, long decodedGeneration, NativeImage image) {
        decoding.remove(key);
        boolean preview = isPreview(key);
        if (generation != decodedGeneration
            || descriptor.session() != key.session() || layout == null
            || (!preview && !requests.isDesired(key))) {
            closeImage(image);
            requests.reject(key, System.currentTimeMillis());
            return;
        }
        if (preview) {
            requests.addDesired(key);
        }
        try {
            DynamicTexture texture = new DynamicTexture(image);
            boolean linear = TacticalMapTextureFilterPolicy.useLinearFiltering(
                key.level(), descriptor.maxLevel());
            TacticalMapTextureSampling.apply(texture, linear);
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
                "espoints_tactical_tile_" + key.session() + "_"
                    + key.level() + "_" + key.x() + "_" + key.y(), texture);
            TextureEntry entry = new TextureEntry(
                location, texture, image.getWidth(), image.getHeight());
            entry.linear = linear;
            boolean evictedAny = false;
            for (TextureEntry evicted : textures.put(key, entry)) {
                release(evicted);
                evictedAny = true;
            }
            readinessRevision++;
            if (evictedAny) {
                readinessRevision++;
            }
            requests.complete(key);
            desiredRanks.remove(key);
            if (preview) {
                ESPointsMod.LOGGER.info("客户端已上传战术地图预览瓦片 {}x{}",
                    image.getWidth(), image.getHeight());
            }
        } catch (RuntimeException error) {
            closeImage(image);
            requests.reject(key, System.currentTimeMillis());
            throw error;
        }
    }

    private synchronized void queueDecoded(
            TacticalMapTileKey key, long decodedGeneration, NativeImage image) {
        // Off-thread NativeImage is not used; accept() queues encoded bytes for
        // render-thread decode. Close anything a leftover worker still produced.
        closeImage(image);
        decoding.remove(key);
    }

    private void releaseAll() {
        requests.clear();
        desiredRanks.clear();
        pendingDecodes.clear();
        decoding.clear();
        lastDesiredPlan = List.of();
        lastDesiredPlanRevision = Long.MIN_VALUE;
        decodedUploadQueue.clear();
        releaseLayerAtlas();
        for (TextureEntry entry : textures.clear()) {
            release(entry);
        }
    }

    private void releaseLayerAtlas() {
        if (layerAtlas != null) {
            release(layerAtlas);
            layerAtlas = null;
            layerAtlasKey = null;
        }
    }

    private synchronized void markDecodeFailed(TacticalMapTileKey key) {
        decoding.remove(key);
        requests.reject(key, System.currentTimeMillis());
    }

    public synchronized long readinessRevision() {
        return readinessRevision;
    }

    public long nativeImagesCreated() {
        return nativeImagesCreated.get();
    }

    public long nativeImagesClosed() {
        return nativeImagesClosed.get();
    }

    public long uploadP95Nanos() {
        return uploadLatencyNanos.percentile(0.95D);
    }

    public long uploadP99Nanos() {
        return uploadLatencyNanos.percentile(0.99D);
    }

    public synchronized int decodedUploadQueueSize() {
        return decodedUploadQueue.size();
    }

    public int queuedDecodeCount() {
        synchronized (this) {
            return decoding.size();
        }
    }

    private synchronized boolean enqueueDecode(DecodeJob incoming) {
        if (decoding.size() >= MAX_QUEUED_DECODES) {
            DecodeJob lowest = pendingDecodes.stream().max(DECODE_ORDER).orElse(null);
            if (lowest == null || DECODE_ORDER.compare(incoming, lowest) >= 0) {
                return false;
            }
            pendingDecodes.remove(lowest);
            decoding.remove(lowest.key());
            requests.reject(lowest.key(), System.currentTimeMillis());
        }
        decoding.add(incoming.key());
        pendingDecodes.add(incoming);
        notifyAll();
        return true;
    }

    private void discardUndesiredQueuedWork() {
        pendingDecodes.removeIf(job -> {
            if (requests.isDesired(job.key())) {
                return false;
            }
            decoding.remove(job.key());
            return true;
        });
        decodedUploadQueue.removeIf(pending -> {
            if (requests.isDesired(pending.key()) || isPreview(pending.key())) {
                return false;
            }
            decoding.remove(pending.key());
            return true;
        });
    }

    private void runDecodeWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            DecodeJob job;
            synchronized (this) {
                while (pendingDecodes.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                job = pendingDecodes.poll();
                boolean preview = isPreview(job.key());
                if (job.generation() != generation
                    || descriptor.session() != job.key().session()
                    || (!preview && !requests.isDesired(job.key()))) {
                    decoding.remove(job.key());
                    requests.reject(job.key(), System.currentTimeMillis());
                    continue;
                }
            }
            try {
                NativeImage image = decode(job.encoded(), job.width(), job.height());
                queueDecoded(job.key(), job.generation(), image);
            } catch (RuntimeException error) {
                markDecodeFailed(job.key());
                ESPointsMod.LOGGER.warn(
                    "战术地图瓦片解码失败: {}", job.key(), error);
            }
        }
    }

    private record PendingUpload(
        TacticalMapTileKey key, long generation, int width, int height, byte[] encoded) {
    }

    private record DecodeJob(
        TacticalMapTileKey key, long generation, int width, int height,
        byte[] encoded, int priority, long sequence) {
    }

    private void tryLoadLocalPreview(TacticalMapTileService.Descriptor incoming) {
        if (incoming == null || !incoming.present() || layout == null) {
            return;
        }
        TacticalMapTileKey preview = new TacticalMapTileKey(
            incoming.session(), incoming.maxLevel(), 0, 0);
        if (tryLoadLocalTile(preview)) {
            ESPointsMod.LOGGER.info("从本地缓存载入战术地图预览");
        }
    }

    private void tryLoadLocalTiles(List<TacticalMapTileKey> keys) {
        if (keys == null || keys.isEmpty() || !descriptor.present() || layout == null) {
            return;
        }
        int loaded = 0;
        for (TacticalMapTileKey key : keys) {
            if (loaded >= MAX_LOCAL_LOADS_PER_UPDATE) {
                break;
            }
            if (tryLoadLocalTile(key)) {
                loaded++;
            }
        }
        if (loaded > 0) {
            ESPointsMod.LOGGER.info("从本地缓存载入 {} 个战术地图瓦片", loaded);
        }
    }

    private boolean tryLoadLocalTile(TacticalMapTileKey key) {
        if (key == null || textures.get(key) != null || decoding.contains(key)) {
            return false;
        }
        Path file = TacticalMapTileService.localTileFile(
            descriptor.sha256(), key.level(), key.x(), key.y());
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            int width = layout.tileWidth(key.level(), key.x());
            int height = layout.tileHeight(key.level(), key.y());
            accept(key.session(), key.level(), key.x(), key.y(), width, height, bytes);
            return textures.get(key) != null || decoding.contains(key)
                || decodedUploadQueue.stream().anyMatch(pending -> pending.key().equals(key));
        } catch (Exception error) {
            ESPointsMod.LOGGER.debug("本地战术地图瓦片读取失败: {}", file, error);
            return false;
        }
    }

    private TacticalMapTileKey previewKeyOrNull() {
        if (!descriptor.present() || layout == null) {
            return null;
        }
        return new TacticalMapTileKey(descriptor.session(), descriptor.maxLevel(), 0, 0);
    }

    private boolean isPreview(TacticalMapTileKey key) {
        return key != null && descriptor.present()
            && key.session() == descriptor.session()
            && key.level() == descriptor.maxLevel()
            && key.x() == 0 && key.y() == 0;
    }

    private void closeImage(NativeImage image) {
        if (image != null) {
            image.close();
            nativeImagesClosed.incrementAndGet();
        }
    }

    private void release(TextureEntry entry) {
        if (entry != null) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
            nativeImagesClosed.incrementAndGet();
        }
    }

    public static final class TextureEntry {
        private final ResourceLocation location;
        private final DynamicTexture texture;
        private final int width;
        private final int height;
        private boolean linear;

        private TextureEntry(ResourceLocation location, DynamicTexture texture,
                             int width, int height) {
            this.location = location;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.linear = false;
        }

        public ResourceLocation location() { return location; }
        public int gpuId() { return texture.getId(); }
        public int width() { return width; }
        public int height() { return height; }
        NativeImage pixels() { return texture.getPixels(); }
        public void prepareFiltering(int level, int maximumLevel,
                                     double scaleX, double scaleY) {
            boolean next = TacticalMapTextureFilterPolicy.useLinearFiltering(
                level, maximumLevel, scaleX, scaleY);
            if (next != linear) {
                TacticalMapTextureSampling.apply(texture, next);
                linear = next;
            }
        }
        private long weight() { return (long) width * height * 4L; }
    }

    public record LayerAtlas(TextureEntry texture, TacticalMapTileAtlasLayout.Spec spec) {
    }

    private record AtlasKey(long session, long generation, long readinessRevision,
                            TacticalMapTileAtlasLayout.Spec spec) {
    }
}
