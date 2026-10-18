package com.example.limbus;

import io.lumine.mythic.lib.MythicLib;
import org.bukkit.plugin.java.JavaPlugin;

public class LimbusSkills extends JavaPlugin {
    @Override
    public void onEnable() {
        MythicLib.plugin.getSkills().registerSkillHandler(new Tanglecleaver(this));
        getLogger().info("Limbus skills loaded: TANGLECLEAVER");
    }
}
