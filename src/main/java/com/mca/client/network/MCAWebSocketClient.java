package com.mca.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mca.MCA;
import com.mca.client.core.MCASplitsTracker;
import com.mca.client.core.MCAStatsStore;
import com.mca.client.core.MCATimer;
import com.mca.client.seed.MCASeedOverride;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Single match WebSocket client for the MCA Fabric client.
 * Authenticates with a token and reconnects with exponential backoff.
 */
public final class MCAWebSocketClient extends WebSocketClient {
    private static final long INITIAL_RECONNECT_MS = 1_000L;
    private static final long MAX_RECONNECT_MS = 30_000L;

    private static final ScheduledExecutorService RECONNECT_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "MCA-WebSocket-Reconnect");
                thread.setDaemon(true);
                return thread;
            }
        });

    private static volatile MCAWebSocketClient instance;
    private final Map<String, Long> opponentSplits =
        new ConcurrentHashMap<String, Long>();
    private volatile String matchId;
    private volatile String seedCategory;
    private volatile boolean queued;
    private volatile String opponentName = "Opponent";
    private volatile int opponentElo = -1;
    private volatile String userId;
    private volatile String selfName;
    private volatile long queuedAtMs;
    private volatile long countdownEndsAtMs;
    private volatile int preMatchElo = -1;
    private volatile int matchFoundSeq;
    private volatile int resultSeq;
    private volatile MatchResult lastResult;
    private volatile String lastError;
    private volatile long lastErrorAtMs;
    private volatile String lastOpponentSplit;
    private volatile long lastOpponentSplitMs;
    private volatile ProfileData profile;
    private final List<LeaderboardEntry> leaderboardEntries =
        new java.util.concurrent.CopyOnWriteArrayList<LeaderboardEntry>();
    private final List<HistoryEntry> historyEntries =
        new java.util.concurrent.CopyOnWriteArrayList<HistoryEntry>();
    private volatile boolean historyLoaded;

    private final Object reconnectLock = new Object();
    private volatile boolean shouldReconnect = true;
    private volatile boolean reconnectScheduled;
    private long reconnectDelayMs = INITIAL_RECONNECT_MS;

    private MCAWebSocketClient(URI uri, Map<String, String> headers) {
        super(uri, headers);
        setConnectionLostTimeout(30);
    }

    /**
     * Creates or returns the authenticated singleton client.
     *
     * Example:
     *   MCAWebSocketClient.connect(
     *       "wss://your-backend.onrender.com",
     *       "base64url-session-token"
     *   );
     */
    public static synchronized MCAWebSocketClient connect(String serverUrl, String authToken) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            MCA.LOGGER.warn("MCA WebSocket server URL is missing");
            return null;
        }
        if (authToken == null || authToken.trim().isEmpty()) {
            MCA.LOGGER.warn("MCA WebSocket authentication token is missing");
            return null;
        }

        URI uri = buildUri(serverUrl, authToken.trim());
        MCAWebSocketClient current = instance;
        if (current != null && current.getURI().equals(uri) && current.isOpen()) {
            return current;
        }
        if (current != null) {
            current.disconnect();
        }

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + authToken.trim());

        MCAWebSocketClient client = new MCAWebSocketClient(uri, headers);
        instance = client;
        client.connect();
        return client;
    }

    /**
     * Builds the WebSocket URI and supplies the token as a query parameter.
     * The query parameter is also accepted by the MCA Node.js backend.
     */
    private static URI buildUri(String serverUrl, String authToken) {
        String base = serverUrl.trim().replaceAll("/+$", "");
        String uriText;

        try {
            URI parsed = URI.create(base);
            String path = parsed.getPath();
            boolean alreadyHasWsPath = path != null && path.matches(".*/ws/?$");
            if (!alreadyHasWsPath) {
                uriText = base + "/ws";
            } else {
                uriText = base;
            }

            String encodedToken = URLEncoder.encode(
                authToken,
                StandardCharsets.UTF_8.name()
            );
            URI uri = URI.create(uriText);
            String oldQuery = uri.getRawQuery();
            uriText = uriText + (oldQuery == null || oldQuery.isEmpty() ? "?" : "&") +
                "token=" + encodedToken;
            return URI.create(uriText);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                "Invalid MCA WebSocket server URL: " + serverUrl,
                exception
            );
        }
    }

    /**
     * Returns the active client or null before the first successful connection setup.
     */
    public static MCAWebSocketClient getInstance() {
        return instance;
    }

    /**
     * Closes the connection without reconnecting.
     */
    public synchronized void disconnect() {
        shouldReconnect = false;
        if (!isClosed()) {
            close(1000, "Client disconnected");
        }
    }

    /**
     * Sends a split packet only while connected.
     */
    public void sendSplit(String split, long splitTimeMs) {
        if (!isOpen()) return;
        if (split == null || split.trim().isEmpty()) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "match.split");
        packet.addProperty("split", split);
        packet.addProperty("splitName", split);
        packet.addProperty("splitTimeMs", splitTimeMs);
        sendSafe(packet.toString());
    }

    /**
     * Sends the final RTA value to finish the match on the server.
     */
    public void sendFinish(long finishTimeMs) {
        if (finishTimeMs < 0) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "match.finish");
        packet.addProperty("finishTimeMs", finishTimeMs);
        sendSafe(packet.toString());
    }

    /**
     * Joins or leaves the matchmaking queue.
     */
    public void setQueueJoined(boolean joined) {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", joined ? "queue.join" : "queue.leave");
        sendSafe(packet.toString());
    }

    /**
     * Forfeits the running match. The server enforces a minimum match time.
     */
    public void sendForfeit() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "match.forfeit");
        sendSafe(packet.toString());
    }

    public void sendMatchReady() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "match.ready");
        sendSafe(packet.toString());
    }

    /**
     * Requests top players and profile data from the backend.
     */
    public void requestLeaderboard() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "leaderboard.get");
        packet.addProperty("limit", 50);
        sendSafe(packet.toString());
    }

    public void requestProfile() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "profile.get");
        sendSafe(packet.toString());
    }

    /**
     * Requests this player's most recent finished matches.
     */
    public void requestHistory() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "match.history");
        packet.addProperty("limit", 25);
        sendSafe(packet.toString());
    }

    /**
     * Serializes all outbound packets through one guarded send method.
     */
    private void sendSafe(String json) {
        if (!isOpen()) return;
        try {
            send(json);
        } catch (RuntimeException exception) {
            MCA.LOGGER.warn("MCA could not send WebSocket packet", exception);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        synchronized (reconnectLock) {
            reconnectScheduled = false;
            reconnectDelayMs = INITIAL_RECONNECT_MS;
        }
        MCA.LOGGER.info("MCA WebSocket connected: {}", getURI());
        requestLeaderboard();
        requestProfile();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject packet = new JsonParser().parse(message).getAsJsonObject();
            String type = packet.has("type") ? packet.get("type").getAsString() : "";

            if ("session.ready".equals(type)) {
                if (packet.has("user") && packet.get("user").isJsonObject()) {
                    JsonObject user = packet.getAsJsonObject("user");
                    if (user.has("id")) userId = user.get("id").getAsString();
                    if (user.has("username")) selfName = user.get("username").getAsString();
                }
                MCA.LOGGER.info("MCA authenticated WebSocket session is ready");
            } else if ("queue.joined".equals(type)) {
                if (!queued) queuedAtMs = System.currentTimeMillis();
                queued = true;
                MCA.LOGGER.info("MCA matchmaking queue joined");
            } else if ("queue.left".equals(type)) {
                queued = false;
                MCA.LOGGER.info("MCA matchmaking queue left");
            } else if ("match.found".equals(type)) {
                handleMatchFound(packet);
            } else if ("match.countdown".equals(type)) {
                long seconds = packet.has("countdownSeconds")
                    ? packet.get("countdownSeconds").getAsLong()
                    : 3L;
                countdownEndsAtMs = System.currentTimeMillis() + seconds * 1000L;
                MCATimer.start();
                // Split times are relative to the timer, which was just restarted.
                MCASplitsTracker.reset();
            } else if ("opponent.split".equals(type)) {
                handleOpponentSplit(packet);
            } else if ("leaderboard.list".equals(type)) {
                handleLeaderboard(packet);
            } else if ("profile.data".equals(type)) {
                handleProfile(packet);
            } else if ("match.history.list".equals(type)) {
                handleHistory(packet);
            } else if ("match.finished".equals(type)) {
                MCA.LOGGER.info("MCA match finished");
                handleMatchFinished(packet);
            } else if ("error".equals(type)) {
                String error = packet.has("message")
                    ? packet.get("message").getAsString()
                    : "Unknown server error";
                lastError = error;
                lastErrorAtMs = System.currentTimeMillis();
                MCA.LOGGER.warn("MCA WebSocket server error: {}", error);
            }
        } catch (RuntimeException exception) {
            MCA.LOGGER.warn("MCA ignored malformed WebSocket packet: {}", message, exception);
        }
    }

    /**
     * Applies the match seed and initializes the local timer.
     */
    private void handleMatchFound(JsonObject packet) {
        opponentSplits.clear();
        lastOpponentSplit = null;
        lastResult = null;
        countdownEndsAtMs = 0L;
        ProfileData known = profile;
        preMatchElo = known != null ? known.elo : -1;
        queued = false;
        matchId = packet.has("matchId") ? packet.get("matchId").getAsString() : null;
        seedCategory = packet.has("seedCategory")
            ? packet.get("seedCategory").getAsString()
            : null;

        if (packet.has("opponent") && packet.get("opponent").isJsonObject()) {
            JsonObject opponent = packet.getAsJsonObject("opponent");
            if (opponent.has("username")) {
                opponentName = opponent.get("username").getAsString();
            }
            opponentElo = opponent.has("elo") ? opponent.get("elo").getAsInt() : -1;
        }

        if (packet.has("seed")) {
            String seed = packet.get("seed").getAsString();
            if (MCASeedOverride.setSeed(seed)) {
                MCA.LOGGER.info("MCA match {} received signed seed {}", matchId, seed);
            } else {
                MCA.LOGGER.warn("MCA received an invalid signed seed: {}", seed);
            }
        }
        matchFoundSeq++;
        sendMatchReady();
    }

    /**
     * Stores a result summary so the UI can show the post-match screen.
     */
    private void handleMatchFinished(JsonObject packet) {
        String winnerId = packet.has("winnerId") && !packet.get("winnerId").isJsonNull()
            ? packet.get("winnerId").getAsString()
            : null;
        String reason = packet.has("resultReason")
            ? packet.get("resultReason").getAsString()
            : "time";
        long winnerTime = packet.has("winnerTimeMs") && packet.get("winnerTimeMs").isJsonPrimitive()
            ? packet.get("winnerTimeMs").getAsLong()
            : -1L;
        int after = packet.has("eloAfter") ? packet.get("eloAfter").getAsInt() : -1;
        int before = packet.has("eloBefore") ? packet.get("eloBefore").getAsInt() : preMatchElo;
        int change = packet.has("eloChange")
            ? packet.get("eloChange").getAsInt()
            : (before >= 0 && after >= 0 ? after - before : 0);
        String me = userId;
        boolean won = winnerId != null && me != null && winnerId.equals(me);

        lastResult = new MatchResult(
            won, reason, winnerTime, before, after, change,
            opponentName, opponentElo, selfName, seedCategory
        );
        matchId = null;
        countdownEndsAtMs = 0L;
        resultSeq++;
        recordRunStats(won, reason, winnerTime);
        requestProfile();
        requestLeaderboard();
    }

    /**
     * Saves this match's splits to the local stats file. A failure here must
     * never stop the result screen from opening.
     */
    private void recordRunStats(boolean won, String reason, long winnerTimeMs) {
        try {
            Map<String, Long> splits = new LinkedHashMap<String, Long>();
            for (Map.Entry<MCASplitsTracker.Split, Long> entry
                : MCASplitsTracker.getSplits().entrySet()) {
                splits.put(entry.getKey().name(), entry.getValue());
            }
            Long finish = splits.get(MCASplitsTracker.Split.KILL_DRAGON.name());
            long timeMs = finish != null ? finish : (won ? winnerTimeMs : -1L);
            MCAStatsStore.recordRun(won, reason, timeMs, opponentName, splits);
        } catch (RuntimeException exception) {
            MCA.LOGGER.warn("MCA could not record run stats", exception);
        }
    }

    private void handleOpponentSplit(JsonObject packet) {
        if (!packet.has("split")) return;
        String split = packet.get("split").getAsString();
        long splitTimeMs = packet.has("splitTimeMs")
            && packet.get("splitTimeMs").isJsonPrimitive()
            ? packet.get("splitTimeMs").getAsLong()
            : 0L;
        opponentSplits.put(split, splitTimeMs);
        lastOpponentSplit = split;
        lastOpponentSplitMs = splitTimeMs;
    }

    private void handleLeaderboard(JsonObject packet) {
        if (!packet.has("entries") || !packet.get("entries").isJsonArray()) return;

        JsonArray entries = packet.getAsJsonArray("entries");
        List<LeaderboardEntry> result = new ArrayList<LeaderboardEntry>();
        for (int index = 0; index < entries.size(); index++) {
            JsonObject row = entries.get(index).getAsJsonObject();
            result.add(new LeaderboardEntry(
                row.has("rank") ? row.get("rank").getAsInt() : index + 1,
                row.has("username") ? row.get("username").getAsString() : "Unknown",
                row.has("elo") ? row.get("elo").getAsInt() : 0,
                row.has("wins") ? row.get("wins").getAsInt() : 0,
                row.has("losses") ? row.get("losses").getAsInt() : 0
            ));
        }
        leaderboardEntries.clear();
        leaderboardEntries.addAll(result);
    }

    private void handleProfile(JsonObject packet) {
        if (!packet.has("profile") || packet.get("profile").isJsonNull()) {
            profile = null;
            return;
        }
        JsonObject row = packet.getAsJsonObject("profile");
        profile = new ProfileData(
            row.has("username") ? row.get("username").getAsString() : "Unknown",
            row.has("elo") ? row.get("elo").getAsInt() : 0,
            row.has("wins") ? row.get("wins").getAsInt() : 0,
            row.has("losses") ? row.get("losses").getAsInt() : 0,
            row.has("rank") ? row.get("rank").getAsInt() : 0
        );
    }

    private void handleHistory(JsonObject packet) {
        if (!packet.has("entries") || !packet.get("entries").isJsonArray()) return;

        JsonArray entries = packet.getAsJsonArray("entries");
        List<HistoryEntry> result = new ArrayList<HistoryEntry>();
        for (int index = 0; index < entries.size(); index++) {
            if (!entries.get(index).isJsonObject()) continue;
            JsonObject row = entries.get(index).getAsJsonObject();
            boolean hasChange = hasValue(row, "eloChange");
            result.add(new HistoryEntry(
                optString(row, "opponent", "Unknown"),
                optInt(row, "opponentElo", -1),
                row.has("won") && row.get("won").getAsBoolean(),
                optString(row, "reason", "time"),
                optLong(row, "timeMs", -1L),
                optLong(row, "opponentTimeMs", -1L),
                hasChange,
                hasChange ? row.get("eloChange").getAsInt() : 0,
                optString(row, "seedCategory", null),
                parseTimestamp(optString(row, "finishedAt", null))
            ));
        }
        historyEntries.clear();
        historyEntries.addAll(result);
        historyLoaded = true;
    }

    private static boolean hasValue(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull();
    }

    private static String optString(JsonObject row, String key, String fallback) {
        return hasValue(row, key) ? row.get(key).getAsString() : fallback;
    }

    private static int optInt(JsonObject row, String key, int fallback) {
        return hasValue(row, key) ? row.get(key).getAsInt() : fallback;
    }

    private static long optLong(JsonObject row, String key, long fallback) {
        return hasValue(row, key) ? row.get(key).getAsLong() : fallback;
    }

    private static long parseTimestamp(String iso) {
        if (iso == null) return -1L;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    public List<HistoryEntry> getHistoryEntries() {
        return new ArrayList<HistoryEntry>(historyEntries);
    }

    /** False until the first history packet arrives, so screens can show "Loading". */
    public boolean isHistoryLoaded() {
        return historyLoaded;
    }

    public List<LeaderboardEntry> getLeaderboardEntries() {
        return new ArrayList<LeaderboardEntry>(leaderboardEntries);
    }

    public ProfileData getProfile() {
        return profile;
    }

    public Map<String, Long> getOpponentSplits() {
        return new ConcurrentHashMap<String, Long>(opponentSplits);
    }

    public String getOpponentName() {
        return opponentName;
    }

    /** Opponent ELO at match start, or -1 when unknown. */
    public int getOpponentElo() {
        return opponentElo;
    }

    public String getUserId() {
        return userId;
    }

    public String getSelfName() {
        return selfName;
    }

    /** Wall-clock time the current queue search started. */
    public long getQueuedAtMs() {
        return queuedAtMs;
    }

    /** Wall-clock time the 3-2-1 countdown ends, or 0 before it starts. */
    public long getCountdownEndsAtMs() {
        return countdownEndsAtMs;
    }

    /** Increments every time the server finds a match. */
    public int getMatchFoundSeq() {
        return matchFoundSeq;
    }

    /** Increments every time a match result arrives. */
    public int getResultSeq() {
        return resultSeq;
    }

    public MatchResult getLastResult() {
        return lastResult;
    }

    /** Last server error message if it arrived within maxAgeMs, else null. */
    public String getRecentError(long maxAgeMs) {
        String error = lastError;
        if (error == null) return null;
        return System.currentTimeMillis() - lastErrorAtMs <= maxAgeMs ? error : null;
    }

    public String getLastOpponentSplit() {
        return lastOpponentSplit;
    }

    public long getLastOpponentSplitMs() {
        return lastOpponentSplitMs;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getSeedCategory() {
        return seedCategory;
    }

    /** True while this player is waiting in the matchmaking queue. */
    public boolean isQueued() {
        return queued;
    }

    /** True from "match.found" until the server reports "match.finished". */
    public boolean isInMatch() {
        return matchId != null;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        MCA.LOGGER.warn(
            "MCA WebSocket closed: code={}, remote={}, reason={}",
            code,
            remote,
            reason
        );
        queued = false;
        if (shouldReconnect && instance == this) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception exception) {
        MCA.LOGGER.error("MCA WebSocket error", exception);
    }

    /**
     * Schedules one reconnect attempt using exponential backoff.
     */
    private void scheduleReconnect() {
        synchronized (reconnectLock) {
            if (reconnectScheduled || !shouldReconnect || instance != this) return;
            reconnectScheduled = true;
            final long delay = reconnectDelayMs;
            reconnectDelayMs = Math.min(reconnectDelayMs * 2L, MAX_RECONNECT_MS);

            RECONNECT_EXECUTOR.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (reconnectLock) {
                        reconnectScheduled = false;
                    }
                    if (!shouldReconnect || instance != MCAWebSocketClient.this) return;
                    MCA.LOGGER.info("MCA WebSocket reconnecting in {} ms", delay);
                    reconnect();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    public static final class MatchResult {
        public final boolean won;
        public final String reason;
        public final long winnerTimeMs;
        public final int eloBefore;
        public final int eloAfter;
        public final int eloChange;
        public final String opponentName;
        public final int opponentElo;
        public final String selfName;
        public final String seedCategory;

        public MatchResult(
            boolean won, String reason, long winnerTimeMs, int eloBefore, int eloAfter,
            int eloChange, String opponentName, int opponentElo, String selfName,
            String seedCategory
        ) {
            this.won = won;
            this.reason = reason;
            this.winnerTimeMs = winnerTimeMs;
            this.eloBefore = eloBefore;
            this.eloAfter = eloAfter;
            this.eloChange = eloChange;
            this.opponentName = opponentName;
            this.opponentElo = opponentElo;
            this.selfName = selfName;
            this.seedCategory = seedCategory;
        }

        public boolean isForfeit() {
            return "forfeit".equals(reason);
        }
    }

    public static final class ProfileData {
        public final String username;
        public final int elo;
        public final int wins;
        public final int losses;
        public final int rank;

        public ProfileData(String username, int elo, int wins, int losses, int rank) {
            this.username = username;
            this.elo = elo;
            this.wins = wins;
            this.losses = losses;
            this.rank = rank;
        }
    }

    public static final class HistoryEntry {
        public final String opponent;
        public final int opponentElo;
        public final boolean won;
        public final String reason;
        public final long timeMs;
        public final long opponentTimeMs;
        public final boolean hasEloChange;
        public final int eloChange;
        public final String seedCategory;
        public final long finishedAtMs;

        public HistoryEntry(
            String opponent, int opponentElo, boolean won, String reason, long timeMs,
            long opponentTimeMs, boolean hasEloChange, int eloChange, String seedCategory,
            long finishedAtMs
        ) {
            this.opponent = opponent;
            this.opponentElo = opponentElo;
            this.won = won;
            this.reason = reason;
            this.timeMs = timeMs;
            this.opponentTimeMs = opponentTimeMs;
            this.hasEloChange = hasEloChange;
            this.eloChange = eloChange;
            this.seedCategory = seedCategory;
            this.finishedAtMs = finishedAtMs;
        }

        public boolean isForfeit() {
            return "forfeit".equals(reason);
        }
    }

    public static final class LeaderboardEntry {
        public final int rank;
        public final String username;
        public final int elo;
        public final int wins;
        public final int losses;

        public LeaderboardEntry(int rank, String username, int elo, int wins, int losses) {
            this.rank = rank;
            this.username = username;
            this.elo = elo;
            this.wins = wins;
            this.losses = losses;
        }
    }
}
