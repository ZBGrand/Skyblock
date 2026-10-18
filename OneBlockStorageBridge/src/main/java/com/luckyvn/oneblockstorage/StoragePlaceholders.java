package com.luckyvn.oneblockstorage;

import com.luckyvn.oneblockcore.storage.StorageFeature;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identifier: obstorage
 *
 * %obstorage_ready%               true/false
 * %obstorage_total%               tổng block trong kho
 * %obstorage_total_formatted%     có dấu phẩy ngăn cách
 * %obstorage_capacity%            dung lượng tối đa
 * %obstorage_capacity_formatted%
 * %obstorage_pending%             lượng đang giữ chỗ
 * %obstorage_free%                capacity - total - pending (chỉ để hiển thị, không dùng để quyết định)
 * %obstorage_used_percent%        % dung lượng đã dùng (gồm giữ chỗ)
 * %obstorage_types%               số loại block đang có
 * %obstorage_filter%              chữ Bật/Tắt theo config
 * %obstorage_filter_boolean%      true/false
 * %obstorage_max_withdraw%        giới hạn rút mỗi lần theo config kho
 * %obstorage_sell_enabled%        true/false
 * %obstorage_balance_<MATERIAL>%            vd: %obstorage_balance_stone%
 * %obstorage_balance_<MATERIAL>_formatted%
 * %obstorage_price_<MATERIAL>%              giá bán một block
 */
public final class StoragePlaceholders extends PlaceholderExpansion {

    private record Snapshot(long time, long total, long pending, Map<Material, Long> balances) {
    }

    private final OneBlockStorageBridge bridge;
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    private volatile String notReady;
    private volatile String filterOn;
    private volatile String filterOff;
    private volatile long cacheMs;

    public StoragePlaceholders(OneBlockStorageBridge bridge) {
        this.bridge = bridge;
        reloadSettings();
    }

    public void reloadSettings() {
        var config = bridge.getConfig();
        notReady = config.getString("placeholders.not-ready", "N/A");
        filterOn = config.getString("placeholders.filter-on", "Bật");
        filterOff = config.getString("placeholders.filter-off", "Tắt");
        cacheMs = Math.max(0L, config.getLong("placeholders.cache-ms", 1000L));
        cache.clear();
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "obstorage";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LuckyVN";
    }

    @Override
    public @NotNull String getVersion() {
        return bridge.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        StorageFeature storage = bridge.storage();
        if (key.equals("ready")) {
            return String.valueOf(storage != null);
        }
        if (storage == null) {
            return notReady;
        }

        try {
            UUID uuid = player.getUniqueId();

            switch (key) {
                case "capacity" -> {
                    return String.valueOf(storage.getConfig().getCapacity());
                }
                case "capacity_formatted" -> {
                    return format(storage.getConfig().getCapacity());
                }
                case "max_withdraw" -> {
                    return String.valueOf(storage.getConfig().getMaxWithdrawPerClick());
                }
                case "sell_enabled" -> {
                    return String.valueOf(storage.getPrices().isEnabled());
                }
                case "filter" -> {
                    return storage.isFilterEnabled(uuid) ? filterOn : filterOff;
                }
                case "filter_boolean" -> {
                    return String.valueOf(storage.isFilterEnabled(uuid));
                }
                default -> {
                }
            }

            if (key.startsWith("price_")) {
                Material material = Material.matchMaterial(key.substring("price_".length()));
                if (material == null) {
                    return null;
                }
                return String.valueOf(storage.getPrices().getPrice(material));
            }

            // Các placeholder dưới đây cần số dư: dùng snapshot cache ngắn hạn.
            Snapshot snap = snapshot(uuid, storage);
            int capacity = storage.getConfig().getCapacity();

            switch (key) {
                case "total" -> {
                    return String.valueOf(snap.total());
                }
                case "total_formatted" -> {
                    return format(snap.total());
                }
                case "pending" -> {
                    return String.valueOf(snap.pending());
                }
                case "free" -> {
                    return String.valueOf(Math.max(0L, capacity - snap.total() - snap.pending()));
                }
                case "used_percent" -> {
                    if (capacity <= 0) {
                        return "0.0";
                    }
                    double percent = (snap.total() + snap.pending()) * 100.0 / capacity;
                    return String.format(Locale.US, "%.1f", Math.min(100.0, percent));
                }
                case "types" -> {
                    return String.valueOf(snap.balances().values().stream().filter(v -> v != null && v > 0).count());
                }
                default -> {
                }
            }

            if (key.startsWith("balance_")) {
                String rest = key.substring("balance_".length());
                boolean formatted = rest.endsWith("_formatted");
                if (formatted) {
                    rest = rest.substring(0, rest.length() - "_formatted".length());
                }
                Material material = Material.matchMaterial(rest);
                if (material == null) {
                    return null;
                }
                long amount = snap.balances().getOrDefault(material, 0L);
                return formatted ? format(amount) : String.valueOf(amount);
            }
        } catch (Exception ex) {
            bridge.getLogger().warning("Lỗi placeholder %obstorage_" + params + "%: " + ex);
            return notReady;
        }
        return null;
    }

    private Snapshot snapshot(UUID uuid, StorageFeature storage) throws Exception {
        long now = System.currentTimeMillis();
        Snapshot cached = cache.get(uuid);
        if (cached != null && now - cached.time() < cacheMs) {
            return cached;
        }
        var store = storage.getStore();
        Snapshot fresh = new Snapshot(
                now,
                store.getTotalBalance(uuid),
                store.getPendingReservedAmount(uuid),
                new HashMap<>(store.getBalances(uuid)));
        if (cache.size() > 1000) {
            cache.clear();
        }
        cache.put(uuid, fresh);
        return fresh;
    }

    private static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
