package com.artillexstudios.axplayerwarps.api.events;

import com.artillexstudios.axplayerwarps.warps.Warp;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called before a player pays to sponsor a warp. The price can be changed, or the purchase cancelled.
 */
public class AxPlayerWarpsSponsorEvent extends Event implements Cancellable {
    private static final HandlerList handlerList = new HandlerList();
    private final Player player;
    private final Warp warp;
    private final String tier;
    private final long durationMillis;
    private double price;
    private boolean cancelled = false;

    public AxPlayerWarpsSponsorEvent(Player player, Warp warp, String tier, long durationMillis, double price) {
        super(!Bukkit.isPrimaryThread());

        this.player = player;
        this.warp = warp;
        this.tier = tier;
        this.durationMillis = durationMillis;
        this.price = price;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    public Player getPlayer() {
        return player;
    }

    public Warp getWarp() {
        return warp;
    }

    public String getTier() {
        return tier;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = Math.max(0, price);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
