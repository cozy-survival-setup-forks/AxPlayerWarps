package com.artillexstudios.axplayerwarps.commands.subcommands;

import com.artillexstudios.axplayerwarps.guis.SponsorGui;
import com.artillexstudios.axplayerwarps.sponsor.SponsorConfig;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.MESSAGEUTILS;

public enum Sponsor {
    INSTANCE;

    public void execute(Player sender, @Nullable Warp warp) {
        if (!SponsorConfig.isEnabled()) {
            MESSAGEUTILS.sendLang(sender, "sponsor.errors.disabled");
            return;
        }

        if (warp == null) {
            List<Warp> own = WarpManager.getWarps(sender);
            if (own.isEmpty()) {
                MESSAGEUTILS.sendLang(sender, "sponsor.errors.no-warps");
                return;
            }
            if (own.size() > 1) {
                MESSAGEUTILS.sendLang(sender, "sponsor.errors.choose-warp");
                return;
            }
            warp = own.get(0);
        }

        new SponsorGui(sender, warp).open();
    }
}
