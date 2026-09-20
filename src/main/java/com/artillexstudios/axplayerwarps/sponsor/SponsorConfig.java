package com.artillexstudios.axplayerwarps.sponsor;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.libs.boostedyaml.dvs.versioning.BasicVersioning;
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.utils.TimeUtils;
import org.bukkit.Material;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads sponsor.yml: the rules for sponsored warps and the tiers players can buy.
 */
public final class SponsorConfig {
    private static Config config;
    private static List<SponsorTier> tiers = List.of();

    private SponsorConfig() {
    }

    public static void load() {
        config = new Config(
                new File(AxPlayerWarps.getInstance().getDataFolder(), "sponsor.yml"),
                AxPlayerWarps.getInstance().getResource("sponsor.yml"),
                GeneralSettings.builder().setUseDefaults(false).build(),
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build()
        );
        readTiers();
    }

    public static boolean reload() {
        boolean success = config.reload();
        if (success) readTiers();
        return success;
    }

    private static void readTiers() {
        List<SponsorTier> loaded = new ArrayList<>();
        Section section = config.getSection("tiers");
        if (section != null) {
            String defaultCurrency = config.getString("currency", "Vault");
            for (String id : section.getRoutesAsStrings(false)) {
                Section tier = section.getSection(id);
                if (tier == null) continue;

                long duration;
                try {
                    duration = TimeUtils.timeFromString(tier.getString("duration", "1d"));
                } catch (NumberFormatException ex) {
                    duration = 0;
                }
                if (duration <= 0) {
                    AxPlayerWarps.getInstance().getLogger().warning("Sponsor tier '" + id + "' has an invalid duration, skipping it. Use values like 4h, 1d or 1w.");
                    continue;
                }

                Material material = Material.matchMaterial(tier.getString("material", "CLOCK"));
                loaded.add(new SponsorTier(
                        id,
                        tier.getString("name", id),
                        duration,
                        Math.max(0, tier.getDouble("price", 0.0)),
                        tier.getString("currency", defaultCurrency),
                        material == null ? Material.CLOCK : material,
                        tier.getStringList("lore"),
                        tier.getInt("slot", -1),
                        tier.getBoolean("glow", false),
                        tier.getString("permission", "")
                ));
            }
        }
        tiers = List.copyOf(loaded);
    }

    public static boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public static boolean isOwnerOnly() {
        return config.getBoolean("owner-only", true);
    }

    /** How many warps can be sponsored at once, 0 for no limit. */
    public static int maxSponsored() {
        return config.getInt("max-sponsored-warps", 10);
    }

    /** How many sponsored warps one player can have at once, 0 for no limit. */
    public static int maxPerPlayer() {
        return config.getInt("max-per-player", 1);
    }

    /** Whether buying again for a sponsored warp adds to the time left. */
    public static boolean stackTime() {
        return config.getBoolean("stack-time", true);
    }

    public static boolean pinToTop() {
        return config.getBoolean("pin-to-top", true);
    }

    public static boolean broadcast() {
        return config.getBoolean("broadcast", true);
    }

    public static List<SponsorTier> tiers() {
        return tiers;
    }

    public static SponsorTier tier(String id) {
        if (id == null) return null;
        for (SponsorTier tier : tiers) {
            if (tier.id().equalsIgnoreCase(id)) return tier;
        }
        return null;
    }
}
