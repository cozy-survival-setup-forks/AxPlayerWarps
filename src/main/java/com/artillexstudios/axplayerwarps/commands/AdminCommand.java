package com.artillexstudios.axplayerwarps.commands;

import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.commands.annotations.AllWarps;
import com.artillexstudios.axplayerwarps.commands.subcommands.Converter;
import com.artillexstudios.axplayerwarps.commands.subcommands.Reload;
import com.artillexstudios.axplayerwarps.enums.AccessList;
import com.artillexstudios.axplayerwarps.enums.Converters;
import com.artillexstudios.axplayerwarps.sponsor.SponsorManager;
import com.artillexstudios.axplayerwarps.utils.TimeUtils;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.DefaultFor;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.exception.CommandErrorException;
import revxrsal.commands.orphan.OrphanCommand;

import java.util.Map;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.LANG;
import static com.artillexstudios.axplayerwarps.AxPlayerWarps.MESSAGEUTILS;

public class AdminCommand implements OrphanCommand {

    @DefaultFor({"~", "~ help"})
    @CommandPermission("axplayerwarps.admin.help")
    public void help(@NotNull CommandSender sender) {
        for (String m : LANG.getStringList("admin-help")) {
            sender.sendMessage(StringUtils.formatToString(m));
        }
    }

    @Subcommand("reload")
    @CommandPermission("axplayerwarps.admin.reload")
    public void reload(@NotNull CommandSender sender) {
        Reload.INSTANCE.execute(sender);
    }

    @Subcommand("delete")
    @CommandPermission("axplayerwarps.admin.delete")
    public void delete(@NotNull CommandSender sender, @AllWarps Warp warp) {
        AxPlayerWarps.getThreadedQueue().submit(() -> {
            AxPlayerWarps.getDatabase().deleteWarp(warp);
            MESSAGEUTILS.sendLang(sender, "admin.deleted", Map.of("%warp%", warp.getName()));
        });
    }

    @Subcommand("deleteid")
    @CommandPermission("axplayerwarps.admin.delete")
    public void deleteId(@NotNull CommandSender sender, int id) {
        AxPlayerWarps.getThreadedQueue().submit(() -> {
            Warp warp = null;
            for (Warp w : WarpManager.getWarps()) {
                if (id != w.getId()) continue;
                warp = w;
                break;
            }
            if (warp == null) {
                MESSAGEUTILS.sendLang(sender, "errors.not-found", Map.of("%warp%", String.valueOf(id)));
                throw new CommandErrorException();
            }
            AxPlayerWarps.getDatabase().deleteWarp(warp);
            MESSAGEUTILS.sendLang(sender, "admin.deleted", Map.of("%warp%", warp.getName()));
        });
    }

    @Subcommand("setowner")
    @CommandPermission("axplayerwarps.admin.setowner")
    public void setOwner(@NotNull CommandSender sender, @AllWarps Warp warp, OfflinePlayer player) {
        AxPlayerWarps.getThreadedQueue().submit(() -> {
            warp.setOwner(player.getUniqueId());
            AxPlayerWarps.getDatabase().updateWarp(warp);
            AxPlayerWarps.getDatabase().removeFromList(warp, AccessList.WHITELIST, player);
            AxPlayerWarps.getDatabase().removeFromList(warp, AccessList.BLACKLIST, player);

            MESSAGEUTILS.sendLang(sender, "admin.setowner", Map.of("%warp%", warp.getName(), "%player%", player.getName() == null ? "---" : player.getName()));
        });
    }

    @Subcommand("sponsor")
    @CommandPermission("axplayerwarps.admin.sponsor")
    public void sponsor(@NotNull CommandSender sender, @AllWarps Warp warp, String duration) {
        long millis;
        try {
            millis = TimeUtils.timeFromString(duration);
        } catch (NumberFormatException ex) {
            millis = 0;
        }
        if (millis <= 0) {
            MESSAGEUTILS.sendLang(sender, "sponsor.errors.invalid-duration");
            return;
        }
        SponsorManager.add(warp, millis, "admin");
        MESSAGEUTILS.sendLang(sender, "sponsor.admin.given", Map.of(
                "%warp%", warp.getName(),
                "%duration%", SponsorManager.formatDuration(millis)
        ));
    }

    @Subcommand("unsponsor")
    @CommandPermission("axplayerwarps.admin.sponsor")
    public void unsponsor(@NotNull CommandSender sender, @AllWarps Warp warp) {
        if (!warp.isSponsored()) {
            MESSAGEUTILS.sendLang(sender, "sponsor.errors.not-sponsored", Map.of("%warp%", warp.getName()));
            return;
        }
        SponsorManager.remove(warp, sender);
    }

    @Subcommand("converter")
    @CommandPermission("axplayerwarps.admin.converter")
    public void converter(CommandSender sender, Converters converters) {
        Converter.INSTANCE.execute(sender, converters);
    }
}
