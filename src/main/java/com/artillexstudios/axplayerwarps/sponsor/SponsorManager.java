package com.artillexstudios.axplayerwarps.sponsor;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axintegrations.types.CurrencyIntegration;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.api.events.AxPlayerWarpsSponsorEvent;
import com.artillexstudios.axplayerwarps.guis.SponsorPickGui;
import com.artillexstudios.axplayerwarps.utils.FormatUtils;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.LANG;
import static com.artillexstudios.axplayerwarps.AxPlayerWarps.MESSAGEUTILS;

/**
 * Buying, giving and ending sponsorships. A sponsored warp is listed first and is marked in the menus
 * until its time runs out.
 */
public final class SponsorManager {

    /** Players with a payment on its way, so a double click cannot buy (and pay) twice. */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private SponsorManager() {
    }

    /** Ends sponsorships that ran out, and tells the owner if they are online. */
    public static void start() {
        Scheduler.get().runAsyncTimer(() -> {
            for (Warp warp : WarpManager.snapshot()) {
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

    /** Starts a purchase when the warp is not chosen yet: the only warp is used, or the player picks one. */
    public static void choose(Player player, SponsorTier tier) {
        if (!SponsorConfig.isEnabled()) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.disabled");
            return;
        }
        java.util.List<Warp> own = WarpManager.getWarps(player);
        if (own.isEmpty()) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.no-warps");
        } else if (own.size() == 1) {
            purchase(player, own.get(0), tier);
        } else {
            new SponsorPickGui(player, tier).open();
        }
    }

    /** Checks the rules and takes payment. Runs on the thread of the player who clicked. */
    public static void purchase(Player player, Warp warp, SponsorTier tier) {
        if (!PENDING.add(player.getUniqueId())) return;

        boolean paying = false;
        try {
            paying = begin(player, warp, tier);
        } finally {
            if (!paying) PENDING.remove(player.getUniqueId());
        }
    }

    /** The reason a sponsorship cannot be bought right now, as a lang key, or null when it can. */
    private static String blocked(Warp warp, SponsorTier tier) {
        boolean extending = warp.isSponsored();
        if (extending && !SponsorConfig.stackTime()) return "sponsor.errors.already-sponsored";
        if (!extending) {
            int maxSponsored = SponsorConfig.maxSponsored();
            if (maxSponsored > 0 && WarpManager.snapshot().stream().filter(Warp::isSponsored).count() >= maxSponsored) {
                return "sponsor.errors.slots-full";
            }
            int maxPerPlayer = SponsorConfig.maxPerPlayer();
            if (maxPerPlayer > 0 && WarpManager.getWarps(warp.getOwner()).stream().filter(Warp::isSponsored).count() >= maxPerPlayer) {
                return "sponsor.errors.player-limit";
            }
        }
        return null;
    }

    /** @return true when a payment was started: the player stays locked until it is finished */
    private static boolean begin(Player player, Warp warp, SponsorTier tier) {
        if (!SponsorConfig.isEnabled()) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.disabled");
            return false;
        }
        if (!WarpManager.snapshot().contains(warp)) return false;
        if (SponsorConfig.isOwnerOnly() && !warp.getOwner().equals(player.getUniqueId())) {
            MESSAGEUTILS.sendLang(player, "errors.not-your-warp");
            return false;
        }
        if (!tier.permission().isEmpty() && !player.hasPermission(tier.permission())) {
            MESSAGEUTILS.sendLang(player, "sponsor.errors.no-permission");
            return false;
        }

        String reason = blocked(warp, tier);
        if (reason != null) {
            sendBlocked(player, warp, reason);
            return false;
        }

        AxPlayerWarpsSponsorEvent event = new AxPlayerWarpsSponsorEvent(player, warp, tier.id(), tier.durationMillis(), tier.price());
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        double price = Math.max(0, event.getPrice());

        CurrencyIntegration integration = null;
        CompletableFuture<Boolean> payment = CompletableFuture.completedFuture(true);
        if (price > 0) {
            integration = CurrencyIntegration.one(tier.currency());
            if (integration == null) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.no-currency");
                return false;
            }
            if (integration.getBalance(player) < price) {
                MESSAGEUTILS.sendLang(player, "sponsor.errors.not-enough-balance", Map.of(
                        "%price%", FormatUtils.formatCurrency(integration, price)
                ));
                return false;
            }
            payment = integration.takeBalance(player.getUniqueId(), price);
        }

        CurrencyIntegration paidWith = integration;
        String formattedPrice = price > 0 ? FormatUtils.formatCurrency(integration, price) : LANG.getString("placeholders.free");
        payment.whenComplete((success, error) -> {
            boolean granted = false;
            String refusal = null;
            try {
                if (error != null || !Boolean.TRUE.equals(success)) return;

                // The rules are checked again: things may have changed while the payment was on its way.
                synchronized (SponsorManager.class) {
                    if (!WarpManager.snapshot().contains(warp)) {
                        refusal = "";
                    } else {
                        refusal = blocked(warp, tier);
                        if (refusal == null) {
                            add(warp, tier.durationMillis(), tier.id());
                            granted = true;
                        }
                    }
                }

                if (!granted && paidWith != null && price > 0) {
                    paidWith.giveBalance(player.getUniqueId(), price);
                }
            } finally {
                PENDING.remove(player.getUniqueId());
            }

            boolean done = granted;
            String why = refusal;
            Scheduler.get().run(player, task -> {
                if (!done) {
                    if (why != null && !why.isEmpty()) sendBlocked(player, warp, why);
                    return;
                }
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
            }, () -> {
            });
        });
        return true;
    }

    private static void sendBlocked(Player player, Warp warp, String key) {
        Map<String, String> values = Map.of(
                "%warp%", warp.getName(),
                "%limit%", "" + SponsorConfig.maxPerPlayer()
        );
        MESSAGEUTILS.sendLang(player, key, values);
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
