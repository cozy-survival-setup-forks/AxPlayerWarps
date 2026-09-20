package com.artillexstudios.axplayerwarps.sponsor;

import com.artillexstudios.axplayerwarps.warps.Warp;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets warp items in the menus show extra text only while the warp is sponsored.
 * A lore line that uses a %sponsor_...% placeholder is left out for warps that are not sponsored.
 */
public final class SponsorLore {

    private SponsorLore() {
    }

    public static List<String> filter(List<String> lore, Warp warp) {
        if (SponsorConfig.isEnabled() && warp.isSponsored()) return new ArrayList<>(lore);

        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (!line.contains("%sponsor_")) result.add(line);
        }
        return result;
    }

    /** The item name to use: the special sponsored name if there is one and the warp is sponsored. */
    public static String name(String normal, @Nullable String sponsored, Warp warp) {
        if (sponsored != null && !sponsored.isEmpty() && SponsorConfig.isEnabled() && warp.isSponsored()) return sponsored;
        return normal;
    }
}
