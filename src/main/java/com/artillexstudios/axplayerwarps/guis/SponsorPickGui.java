package com.artillexstudios.axplayerwarps.guis;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axguiframework.GuiFrame;
import com.artillexstudios.axguiframework.item.AxGuiItem;
import com.artillexstudios.axguiframework.libs.gui.guis.Gui;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.sponsor.SponsorLore;
import com.artillexstudios.axplayerwarps.sponsor.SponsorManager;
import com.artillexstudios.axplayerwarps.sponsor.SponsorTier;
import com.artillexstudios.axplayerwarps.user.Users;
import com.artillexstudios.axplayerwarps.user.WarpUser;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Shown when a player with several warps clicks a sponsorship in the categories menu, to pick
 * which warp to sponsor.
 */
public class SponsorPickGui extends GuiFrame<Gui> {
    private static final Config GUI = new Config(new File(AxPlayerWarps.getInstance().getDataFolder(), "guis/sponsor-warps.yml"),
            AxPlayerWarps.getInstance().getResource("guis/sponsor-warps.yml"),
            GeneralSettings.builder().setUseDefaults(false).build(),
            LoaderSettings.builder().build(),
            DumperSettings.DEFAULT,
            UpdaterSettings.builder().build()
    );

    private final SponsorTier tier;
    private final WarpUser user;

    public SponsorPickGui(Player player, SponsorTier tier) {
        super(GUI.getInt("auto-update-ticks", -1), GUI, player);
        this.user = Users.get(player);
        this.tier = tier;
        this.gui = Gui.gui()
                .disableAllInteractions()
                .title(StringUtils.format(GUI.getString("title", ""), Map.of("%tier%", tier.id())))
                .rows(GUI.getInt("rows", 5))
                .create();

        setGui(gui, () -> parseText(GUI.getString("title", "")));
        user.addGui(this);
    }

    public static boolean reload() {
        return GUI.reload();
    }

    public void open() {
        List<Integer> slots = getSlots(GUI.getStringList("warp-slots"));
        int next = 0;
        for (Warp warp : WarpManager.getWarps(player)) {
            if (next >= slots.size()) break;

            ItemBuilder builder = ItemBuilder.create(warp.getIcon());
            builder.setName(parseText(GUI.getString("warp.name", "%name%"), warp));
            builder.setLore(parseText(SponsorLore.filter(GUI.getStringList("warp.lore"), warp), warp));
            if (warp.isSponsored()) builder.glow(true);

            gui.setItem(slots.get(next++), new AxGuiItem(builder.get(), event -> SponsorManager.purchase(player, warp, tier)));
        }

        gui.update();
        gui.open(player);
    }
}
