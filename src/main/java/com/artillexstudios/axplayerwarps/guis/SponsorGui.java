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
import com.artillexstudios.axguiframework.replacements.Replacements;
import com.artillexstudios.axintegrations.types.CurrencyIntegration;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.sponsor.SponsorConfig;
import com.artillexstudios.axplayerwarps.sponsor.SponsorManager;
import com.artillexstudios.axplayerwarps.sponsor.SponsorTier;
import com.artillexstudios.axplayerwarps.user.Users;
import com.artillexstudios.axplayerwarps.user.WarpUser;
import com.artillexstudios.axplayerwarps.utils.FormatUtils;
import com.artillexstudios.axplayerwarps.warps.Warp;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.LANG;

/**
 * The menu where a warp's owner picks how long to sponsor it for. The tiers come from sponsor.yml.
 */
public class SponsorGui extends GuiFrame<Gui> {
    private static final Config GUI = new Config(new File(AxPlayerWarps.getInstance().getDataFolder(), "guis/sponsor.yml"),
            AxPlayerWarps.getInstance().getResource("guis/sponsor.yml"),
            GeneralSettings.builder().setUseDefaults(false).build(),
            LoaderSettings.builder().build(),
            DumperSettings.DEFAULT,
            UpdaterSettings.builder().build()
    );

    private final Warp warp;
    private final WarpUser user;

    public SponsorGui(Player player, Warp warp) {
        super(GUI.getInt("auto-update-ticks", -1), GUI, player);
        this.user = Users.get(player);
        this.warp = warp;
        this.gui = Gui.gui()
                .disableAllInteractions()
                .title(StringUtils.format(GUI.getString("title", ""), Map.of("%warp%", warp.getName())))
                .rows(GUI.getInt("rows", 3))
                .create();

        addReplacement(new Replacements("%warp%", warp.getName()));
        addPlaceholderParameter(warp);

        setGui(gui, () -> parseText(GUI.getString("title", "")));
        user.addGui(this);
    }

    public static boolean reload() {
        return GUI.reload();
    }

    public void open() {
        List<SponsorTier> tiers = SponsorConfig.tiers();

        // Tiers with their own slot go there, the rest fill the free slots from tier-slots in order.
        Set<Integer> taken = new HashSet<>();
        for (SponsorTier tier : tiers) {
            if (tier.slot() >= 0) taken.add(tier.slot());
        }
        List<Integer> free = new ArrayList<>(getSlots(GUI.getStringList("tier-slots")));
        free.removeAll(taken);

        int next = 0;
        for (SponsorTier tier : tiers) {
            int slot = tier.slot();
            if (slot < 0) {
                if (next >= free.size()) continue;
                slot = free.get(next++);
            }
            if (slot >= gui.getRows() * 9) continue;
            gui.setItem(slot, tierItem(tier));
        }

        gui.update();
        gui.open(player);
    }

    private AxGuiItem tierItem(SponsorTier tier) {
        CurrencyIntegration integration = CurrencyIntegration.one(tier.currency());
        String price = tier.price() <= 0
                ? LANG.getString("placeholders.free")
                : FormatUtils.formatCurrency(integration, tier.price());

        Map<String, String> values = Map.of(
                "%tier%", tier.id(),
                "%duration%", SponsorManager.formatDuration(tier.durationMillis()),
                "%price%", price,
                "%warp%", warp.getName()
        );

        List<String> lore = new ArrayList<>();
        for (String line : tier.lore().isEmpty() ? GUI.getStringList("tier.lore") : tier.lore()) {
            lore.add(replace(line, values));
        }

        ItemBuilder builder = ItemBuilder.create(tier.material());
        builder.setName(parseText(replace(tier.name(), values)));
        builder.setLore(parseText(lore));
        builder.glow(tier.glow());
        return new AxGuiItem(builder.get(), event -> SponsorManager.purchase(player, warp, tier));
    }

    private static String replace(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
