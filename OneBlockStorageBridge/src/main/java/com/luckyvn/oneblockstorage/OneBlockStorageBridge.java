package com.luckyvn.oneblockstorage;

import com.luckyvn.oneblockcore.OneBlockCore;
import com.luckyvn.oneblockcore.storage.StorageFeature;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class OneBlockStorageBridge extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private StoragePlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginCommand command = getCommand("obstorage");
        if (command == null) {
            getLogger().severe("Thiếu lệnh obstorage trong plugin.yml, tắt plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ObsCommand handler = new ObsCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholders = new StoragePlaceholders(this);
            placeholders.register();
            getLogger().info("Đã đăng ký PlaceholderAPI expansion: %obstorage_*%");
        } else {
            getLogger().warning("Không có PlaceholderAPI, bỏ qua placeholder.");
        }
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
    }

    /**
     * Lấy kho đang sẵn sàng, hoặc null. Không cache kết quả: reload kho có thể thay đối tượng.
     */
    public StorageFeature storage() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("OneBlockCore");
        if (!(plugin instanceof OneBlockCore core) || !core.isEnabled()) {
            return null;
        }
        StorageFeature storage = core.getStorageFeature();
        return (storage != null && storage.isReady()) ? storage : null;
    }

    /** Xóa cache placeholder của một người chơi sau khi số dư thay đổi. */
    public void invalidate(UUID uuid) {
        if (placeholders != null) {
            placeholders.invalidate(uuid);
        }
    }

    public void reload() {
        reloadConfig();
        if (placeholders != null) {
            placeholders.reloadSettings();
        }
    }

    /** Gửi tin nhắn từ config. pairs: key1, value1, key2, value2... */
    public void send(CommandSender to, String key, String... pairs) {
        String prefix = getConfig().getString("messages.prefix", "");
        if (key.equals("help")) {
            List<String> lines = getConfig().getStringList("messages.help");
            for (String line : lines) {
                to.sendMessage(LEGACY.deserialize(line));
            }
            return;
        }
        String text = getConfig().getString("messages." + key, key);
        to.sendMessage(LEGACY.deserialize(prefix + replace(text, pairs)));
    }

    public static String replace(String text, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return text;
    }
}
