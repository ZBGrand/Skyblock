package com.example.limbus;

import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.result.SimpleSkillResult;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tanglecleaver [快刀亂麻] - dựa trên The Thumb East Capo IIII Meursault (Limbus Company).
 */
public class Tanglecleaver extends SkillHandler<SimpleSkillResult> {

    private static final int MAX_ROUNDS = 12;
    private static final int STAR_THRESHOLD = 8;

    private static class Ammo {
        int rounds = MAX_ROUNDS;
        int spent = 0;
        boolean reloading = false;
        long starUntil = 0;
    }

    private static class Status {
        double potency;
        int count;
        boolean scorch;
    }

    private static final Map<UUID, Ammo> AMMO = new HashMap<>();
    private static final Map<UUID, Status> TREMOR = new HashMap<>();
    private static final Map<UUID, Status> BURN = new HashMap<>();

    private final JavaPlugin plugin;
    private double burnMultiplier = 1.0;

    public Tanglecleaver(JavaPlugin plugin) {
        super();
        this.plugin = plugin;
        registerModifiers("damage-per-coin", "range", "tremor-damage",
                "burn-damage", "reload", "star-duration");
        startBurnTicker();
    }

    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        Player p = meta.getCaster().getPlayer();
        return new SimpleSkillResult(findTarget(p, meta.getParameter("range")) != null);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata meta) {
        Player player = meta.getCaster().getPlayer();
        LivingEntity target = findTarget(player, meta.getParameter("range"));
        if (target == null) return;

        double perCoin = meta.getParameter("damage-per-coin");
        double tremorMult = meta.getParameter("tremor-damage");
        burnMultiplier = meta.getParameter("burn-damage");
        int reloadSec = (int) meta.getParameter("reload");
        int starSec = (int) meta.getParameter("star-duration");

        Ammo ammo = AMMO.computeIfAbsent(player.getUniqueId(), k -> new Ammo());

        new BukkitRunnable() {
            int coin = 1;

            @Override
            public void run() {
                if (!player.isOnline() || target.isDead()) {
                    cancel();
                    return;
                }
                flipCoin(player, target, ammo, coin, perCoin, tremorMult, reloadSec, starSec);
                if (++coin > 3) cancel();
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    private void flipCoin(Player player, LivingEntity target, Ammo ammo, int coin,
                          double perCoin, double tremorMult, int reloadSec, int starSec) {

        boolean spent = ammo.rounds > 0;
        if (spent) {
            ammo.rounds--;
            ammo.spent++;
        }

        double dmg = perCoin * (spent ? 1.1 : 1.0);
        boolean starActive = System.currentTimeMillis() < ammo.starUntil;
        if (starActive) dmg *= 1.2;

        double stacks = potency(TREMOR, target) + potency(BURN, target);
        dmg *= 1 + Math.min(2, (int) (stacks / 8)) * 0.05;

        if (coin == 3 && spent) dmg *= 1.5;

        hit(player, target, dmg);

        int bonus = starActive ? 1 : 0;
        if (spent) {
            if (coin == 1) {
                add(TREMOR, target, 3 + bonus, 1, false);
                add(BURN, target, 3, 1, false);
            } else if (coin == 2) {
                add(TREMOR, target, 0, 3 + bonus, false);
                add(BURN, target, 0, 3, false);
            } else {
                Status t = TREMOR.get(target.getUniqueId());
                if (t != null) t.scorch = true;
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 4;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!target.isDead()) burst(player, target, tremorMult);
                    }, delay);
                }
            }
        }

        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                15, 0.3, 0.4, 0.3, 0.15);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f + coin * 0.2f);

        if (ammo.spent >= STAR_THRESHOLD && !starActive && ammo.starUntil == 0) {
            ammo.starUntil = System.currentTimeMillis() + starSec * 1000L;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, starSec * 20, 1));
            player.sendMessage("§6[天退星] §eTiantui Star kích hoạt!");
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        }

        if (ammo.rounds <= 0 && !ammo.reloading) {
            ammo.reloading = true;
            player.sendActionBar("§c⚠ Hết Tigermark Round! Nạp lại...");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ammo.rounds = MAX_ROUNDS;
                ammo.spent = 0;
                ammo.starUntil = 0;
                ammo.reloading = false;
                player.sendActionBar("§aĐã nạp lại §6" + MAX_ROUNDS + " §aTigermark Round");
            }, reloadSec * 20L);
        } else {
            player.sendActionBar("§6Tigermark: §e" + ammo.rounds + "§7/" + MAX_ROUNDS
                    + "  §8| §cBurn §f" + (int) potency(BURN, target)
                    + " §8| §bTremor §f" + (int) potency(TREMOR, target));
        }
    }

    private void burst(Player player, LivingEntity target, double tremorMult) {
        Status t = TREMOR.get(target.getUniqueId());
        if (t == null) return;

        double dmg = t.potency * tremorMult;
        if (t.scorch) {
            Status b = BURN.get(target.getUniqueId());
            double burnPot = b == null ? 0 : b.potency;
            dmg += (t.potency + burnPot) / 2.0;
            if (b != null && --b.count <= 0) BURN.remove(target.getUniqueId());
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0),
                    25, 0.4, 0.5, 0.4, 0.05);
        } else {
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0),
                    25, 0.4, 0.5, 0.4, 0.1);
        }
        hit(player, target, dmg);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);

        if (--t.count <= 0) TREMOR.remove(target.getUniqueId());
    }

    private void startBurnTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, Status>> it = BURN.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Status> e = it.next();
                    Entity ent = Bukkit.getEntity(e.getKey());
                    if (!(ent instanceof LivingEntity) || ent.isDead()) {
                        it.remove();
                        continue;
                    }
                    LivingEntity le = (LivingEntity) ent;
                    le.setNoDamageTicks(0);
                    le.damage(e.getValue().potency * burnMultiplier);
                    le.getWorld().spawnParticle(Particle.FLAME, le.getLocation().add(0, 1, 0),
                            6, 0.3, 0.4, 0.3, 0.02);
                    if (--e.getValue().count <= 0) it.remove();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void hit(Player player, LivingEntity target, double dmg) {
        target.setNoDamageTicks(0);
        target.damage(dmg, player);
    }

    private void add(Map<UUID, Status> map, LivingEntity target, double potency, int count, boolean scorch) {
        Status s = map.computeIfAbsent(target.getUniqueId(), k -> new Status());
        s.potency = Math.min(99, s.potency + potency);
        s.count = Math.min(99, s.count + count);
        if (scorch) s.scorch = true;
    }

    private double potency(Map<UUID, Status> map, LivingEntity target) {
        Status s = map.get(target.getUniqueId());
        return s == null ? 0 : s.potency;
    }

    private LivingEntity findTarget(Player player, double range) {
        RayTraceResult r = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, 0.6,
                e -> e instanceof LivingEntity && !e.equals(player));
        return r == null ? null : (LivingEntity) r.getHitEntity();
    }

    @Override
    public String getLegacyId() {
        return "TANGLECLEAVER";
    }
}
