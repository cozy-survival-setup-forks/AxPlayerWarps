package com.artillexstudios.axplayerwarps.world;

import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private static final ConcurrentHashMap<World, Integer> worlds = new ConcurrentHashMap<>();

    public static void reload() {
        worlds.clear();

        AxPlayerWarps.getThreadedQueue().submit(() -> {
            for (World world : Bukkit.getWorlds()) {
                worlds.put(world, AxPlayerWarps.getDatabase().getWorldId(world));
            }
        });
    }

    public static ConcurrentHashMap<World, Integer> getWorlds() {
        return worlds;
    }

    public static void onWorldLoad(World world) {
        AxPlayerWarps.getThreadedQueue().submit(() -> {
            WorldManager.getWorlds().put(world, AxPlayerWarps.getDatabase().getWorldId(world));
        });

        for (Warp warp : WarpManager.snapshot()) {
            if (!world.getName().equals(warp.getWorldName())) continue;
            warp.getLocation().setWorld(world);
        }
    }

    public static void onWorldUnload(World world) {
        WorldManager.getWorlds().remove(world);
    }
}
