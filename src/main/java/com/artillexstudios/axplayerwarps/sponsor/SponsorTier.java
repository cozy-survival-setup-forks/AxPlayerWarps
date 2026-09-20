package com.artillexstudios.axplayerwarps.sponsor;

import org.bukkit.Material;

import java.util.List;

/**
 * One thing a player can buy to sponsor a warp, as set up in sponsor.yml.
 */
public record SponsorTier(String id, String name, long durationMillis, double price, String currency,
                          Material material, List<String> lore, int slot, boolean glow, String permission) {
}
