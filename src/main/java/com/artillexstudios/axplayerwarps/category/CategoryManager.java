package com.artillexstudios.axplayerwarps.category;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axplayerwarps.AxPlayerWarps;
import com.artillexstudios.axplayerwarps.warps.Warp;
import com.artillexstudios.axplayerwarps.warps.WarpManager;

import java.util.LinkedHashMap;

import static com.artillexstudios.axplayerwarps.AxPlayerWarps.CONFIG;

public class CategoryManager {
    private static final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();

    public static void reload() {
        categories.clear();

        AxPlayerWarps.getThreadedQueue().submit(() -> {
            if (CONFIG.getSection("categories") == null) return;
            for (String raw : CONFIG.getSection("categories").getRoutesAsStrings(false)) {
                Section section = CONFIG.getSection("categories." + raw);

                String name = section.getString("name");
                int id = AxPlayerWarps.getDatabase().getCategoryId(raw);

                Category category = new Category(id, raw, name, section);
                categories.put(raw, category);
            }

            for (Warp warp : WarpManager.snapshot()) {
                Category curr = warp.getCategory();
                if (curr == null) continue;
                Category nw = categories.get(curr.raw());
                warp.setCategory(nw);
            }
        });
    }

    public static LinkedHashMap<String, Category> getCategories() {
        return categories;
    }
}
