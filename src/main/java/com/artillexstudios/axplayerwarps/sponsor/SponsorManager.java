package com.artillexstudios.axplayerwarps.sponsor;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axintegrations.types.CurrencyIntegration;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.api.events.AxPlayerWarpsSponsorEvent;
import com.artillexstudios.axplayerwarps.utils.FormatUtils;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.LANG;
import static com.artillexstudios.axplayerwarps.AxPlayerWarps.MESSAGEUTILS;

/**
 * Buying, giving and ending sponsorships. A sponsored warp is listed first and is marked in the menus
 * until its time runs out.
 */
public final class SponsorManager {

    private SponsorManager() {
    }

    /** Ends sponsorships that ran out, and tells the owner if they are online. */
    public static void start() {
        Scheduler.get().runAsyncTimer(() -> {
            for (Warp warp : WarpManager.getWarps()) {
                if (warp.getSponsoredUntil() <= 0 || warp.isSponsored()) continue;

                warp.clearSponsor();
                AxPlayerWarps.getDatabase().removeSponsor(warp);

                Player owner = Bukkit.getPlayer(warp.getOwner());
                if (owner != null) {
                    MESSAGEUTILS.sendLang(owner, "sponsor.expired", Map.of("%warp%", warp.getName()));
                }
            }
        }, 600L, 600L);
    }

    /** Checks the rules and takes payment. Runs on the thread of the player who clicked. */
    public static void purchase(Player player, Warp warp, SponsorTier tier) {
        if (!SponsorConfig.isEnabled()) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.disabled");
            return;
        }
        if (SponsorConfig.isOwnerOnly() && !warp.getOwner().equals(player.getUniqueId())) {
            MESSAGEUTILS.sendLang(player, "errors.not-your-warp");
            return;
        }
        if (!tier.permission().isEmpty() && !player.hasPermission(tier.permission())) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.no-permission");
            return;
        }

        boolean extending = warp.isSponsored();
        if (extending && !SponsorConfig.stackTime()) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.already-sponsored", Map.of("%warp%", warp.getName()));
            return;
        }
        if (!extending) {
            int maxSponsored = SponsorConfig.maxSponsored();
            if (maxSponsored > 0 && WarpManager.getWarps().stream().filter(Warp::isSponsored).count() >= maxSponsored) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.slots-full");
                return;
            }
            int maxPerPlayer = SponsorConfig.maxPerPlayer();
            if (maxPerPlayer > 0 && WarpManager.getWarps(warp.getOwner()).stream().filter(Warp::isSponsored).count() >= maxPerPlayer) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.player-limit", Map.of("%limit%", "" + maxPerPlayer));
                return;
            }
        }

        AxPlayerWarpsSponsorEvent event = new AxPlayerWarpsSponsorEvent(player, warp, tier.id(), tier.durationMillis(), tier.price());
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        double price = event.getPrice();

        CurrencyIntegration integration = null;
        CompletableFuture<Boolean> payment = CompletableFuture.completedFuture(true);
        if (price > 0) {
            integration = CurrencyIntegration.one(tier.currency());
            if (integration == null) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.no-currency");
                return;
            }
            if (integration.getBalance(player) < price) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.not-enough-balance", Map.of(
                        "%price%", FormatUtils.formatCurrency(integration, price)
                ));
                return;
            }
            payment = integration.takeBalance(player.getUniqueId(), price);
        }

        String formattedPrice = price > 0 ? FormatUtils.formatCurrency(integration, price) : LANG.getString("placeholders.free");
        payment.thenAccept(success -> {
            if (!success) return;

            add(warp, tier.durationMillis(), tier.id());
            player.closeInventory();
            MESSAGEUTILS.sendLang(player, "sponsor.purchased", Map.of(
                    "%warp%", warp.getName(),
                    "%tier%", tier.id(),
                    "%duration%", formatDuration(tier.durationMillis()),
                    "%time_left%", formatDuration(warp.getSponsorRemaining()),
                    "%price%", formattedPrice
            ));

            if (SponsorConfig.broadcast()) {
                Map<String, String> replacements = Map.of(
                        "%player%", player.getName(),
                        "%warp%", warp.getName(),
                        "%duration%", formatDuration(tier.durationMillis())
                );
                for (Player online : Bukkit.getOnlinePlayers()) {
                    MESSAGEUTILS.sendLang(online, "sponsor.broadcast", replacements);
                }
            }
        });
    }

    /** Adds time to a warp's sponsorship, or starts one. Used by purchases and by admins. */
    public static void add(Warp warp, long durationMillis, String tier) {
        long start = warp.isSponsored() ? warp.getSponsoredUntil() : System.currentTimeMillis();
        long until = start + durationMillis;
        warp.setSponsor(until, tier);
        AxPlayerWarps.getThreadedQueue().submit(() -> AxPlayerWarps.getDatabase().setSponsor(warp, until, tier));
    }

    public static void remove(Warp warp, CommandSender sender) {
        warp.clearSponsor();
        AxPlayerWarps.getThreadedQueue().submit(() -> {
            AxPlayerWarps.getDatabase().removeSponsor(warp);
            MESSAGEUTILS.sendLang(sender, "sponsor.admin.removed", Map.of("%warp%", warp.getName()));
        });
    }

    /** A length of time as "1d 11h", "4h" or "30m", using the units from lang.yml. */
    public static String formatDuration(long millis) {
        long total = Math.max(0, millis / 1000);
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        String d = LANG.getString("time.day", "d");
        String h = LANG.getString("time.hour", "h");
        String m = LANG.getString("time.minute", "m");
        String s = LANG.getString("time.second", "s");

        if (days > 0) return hours > 0 ? days + d + " " + hours + h : days + d;
        if (hours > 0) return minutes > 0 ? hours + h + " " + minutes + m : hours + h;
        if (minutes > 0) return minutes + m;
        return seconds + s;
    }
}
