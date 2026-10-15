package com.cupno.explosivepickaxe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ket noi voi plugin ExtraStorage (HSGamer) bang reflection, nen khong can jar ExtraStorage luc build
 * va chay duoc ca voi ban cu (com.hyronic.exstorage) lan ban moi (me.hsgamer.extrastorage).
 */
public final class ExtraStorageHook {

    /** stored = so luong da vao kho, full = kho da day (khong nhan them duoc nua). */
    public record Result(int stored, boolean full) {
        static final Result NOTHING = new Result(0, false);
    }

    private static final String[] BASE_PACKAGES = {"me.hsgamer.extrastorage", "com.hyronic.exstorage"};

    private final ExplosivePickaxe plugin;

    private boolean ready;
    private Object api;
    private Method getUser;
    private Method getStorage;
    private Method canStore;
    private Method add;
    private Method getFreeSpace;
    private Method getStatus; // co the null

    public ExtraStorageHook(ExplosivePickaxe plugin) {
        this.plugin = plugin;
    }

    private String lastError = "chua thu ket noi";

    public boolean isReady() {
        return ready || init();
    }

    public String getLastError() {
        return lastError;
    }

    /** Tim plugin ExtraStorage (chap nhan ten hoi khac, vd "ExtraStorage-Premium"). */
    private Plugin findPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("ExtraStorage");
        if (p != null) return p;
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other.getName().toLowerCase().contains("extrastorage")) return other;
        }
        return null;
    }

    public boolean init() {
        Plugin es = findPlugin();
        if (es == null) {
            StringBuilder names = new StringBuilder();
            for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
                if (names.length() > 0) names.append(", ");
                names.append(pl.getName());
            }
            lastError = "khong thay plugin nao ten chua 'ExtraStorage'. Plugin dang co: " + names;
            return false;
        }
        if (!es.isEnabled()) {
            lastError = "plugin " + es.getName() + " chua duoc bat (kiem tra log xem no co loi khi khoi dong khong)";
            return false;
        }

        ClassLoader loader = es.getClass().getClassLoader();

        // Danh sach package ung vien: suy ra tu class chinh cua plugin + cac package da biet
        List<String> bases = new ArrayList<>();
        String mainPkg = es.getClass().getPackageName();
        if (!mainPkg.isEmpty()) bases.add(mainPkg);
        for (String known : BASE_PACKAGES) {
            if (!bases.contains(known)) bases.add(known);
        }

        StringBuilder errors = new StringBuilder();
        for (String base : bases) {
            try {
                Class<?> apiClass = Class.forName(base + ".api.StorageAPI", true, loader);
                Class<?> userClass = Class.forName(base + ".api.user.User", true, loader);
                Class<?> storageClass = Class.forName(base + ".api.storage.Storage", true, loader);

                this.api = apiClass.getMethod("getInstance").invoke(null);
                this.getUser = apiClass.getMethod("getUser", UUID.class);
                this.getStorage = userClass.getMethod("getStorage");
                this.canStore = storageClass.getMethod("canStore", Object.class);
                this.add = storageClass.getMethod("add", Object.class, long.class);
                this.getFreeSpace = storageClass.getMethod("getFreeSpace");
                try {
                    this.getStatus = storageClass.getMethod("getStatus");
                } catch (NoSuchMethodException ignored) {
                    this.getStatus = null;
                }
                this.ready = true;
                this.lastError = "OK (" + es.getName() + " " + es.getDescription().getVersion() + ", package " + base + ")";
                return true;
            } catch (Throwable t) {
                Throwable root = t.getCause() != null ? t.getCause() : t;
                errors.append("[").append(base).append("] ").append(root).append(" ");
            }
        }
        lastError = "da thay plugin " + es.getName() + " (main: " + es.getClass().getName()
                + ") nhung khong doc duoc API: " + errors;
        return false;
    }

    /**
     * Cat toi da co the cua stack vao kho ExtraStorage cua nguoi choi.
     * Phan con lai (neu co) do ben goi tu xu ly.
     */
    public Result store(Player player, ItemStack stack) {
        if (!isReady()) return Result.NOTHING;
        try {
            Object user = getUser.invoke(api, player.getUniqueId());
            if (user == null) return Result.NOTHING;
            Object storage = getStorage.invoke(user);
            if (storage == null) return Result.NOTHING;

            // nguoi choi tat kho -> khong luu
            if (getStatus != null && !((Boolean) getStatus.invoke(storage))) return Result.NOTHING;

            ItemStack key = stack.clone();
            key.setAmount(1);
            if (!((Boolean) canStore.invoke(storage, key))) return Result.NOTHING;

            long free = (Long) getFreeSpace.invoke(storage); // -1 = khong gioi han
            int amount = stack.getAmount();
            if (free == 0) return new Result(0, true);

            int toStore = free < 0 ? amount : (int) Math.min(free, amount);
            if (toStore <= 0) return new Result(0, true);

            add.invoke(storage, key, (long) toStore);
            return new Result(toStore, toStore < amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("Loi khi luu vao ExtraStorage: " + t);
            return Result.NOTHING;
        }
    }
}
