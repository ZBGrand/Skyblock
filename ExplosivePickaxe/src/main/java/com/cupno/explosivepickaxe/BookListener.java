package com.cupno.explosivepickaxe;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Nguoi choi cam sach cup no tren con tro roi click vao cup trong tui do de enchant. */
public final class BookListener implements Listener {

    private final ExplosivePickaxe plugin;

    public BookListener(ExplosivePickaxe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        int bookSize = PickaxeItem.getBookSize(plugin, cursor);
        if (bookSize <= 0) return;

        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR) return;

        // Da cam sach: chan hanh vi mac dinh khi click len vat pham khac
        if (!PickaxeItem.isPickaxe(target)) {
            return; // click vao o khong phai cup -> de nguoi choi di chuyen sach binh thuong
        }
        event.setCancelled(true);

        if (!player.hasPermission("explosivepickaxe.use")) {
            plugin.send(player, "no-permission");
            return;
        }

        int current = PickaxeItem.getSize(plugin, target);
        boolean allowDowngrade = plugin.getConfig().getBoolean("enchant.allow-downgrade", false);
        if (current > 0 && !allowDowngrade && bookSize <= current) {
            plugin.send(player, "book-not-better", "current", String.valueOf(current));
            return;
        }

        if (!PickaxeItem.enchant(plugin, target, bookSize)) {
            plugin.send(player, "book-not-pickaxe");
            return;
        }

        // Tieu hao 1 sach
        if (cursor.getAmount() <= 1) {
            event.setCursor(null);
        } else {
            ItemStack rest = cursor.clone();
            rest.setAmount(cursor.getAmount() - 1);
            event.setCursor(rest);
        }
        event.setCurrentItem(target);

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        plugin.send(player, "enchanted", "size", String.valueOf(bookSize));
    }
}
