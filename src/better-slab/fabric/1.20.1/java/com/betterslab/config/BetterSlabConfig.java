package com.betterslab.config;

import com.betterslab.BetterSlab;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置文件。
 * 配置文件位于 config/betterslab.json，只包含功能开关。
 * 按键绑定通过 Fabric KeyBinding API 注册，可在游戏设置中修改。
 */
public class BetterSlabConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("betterslab.json");

    public boolean verticalSlab = true;
    public boolean selectiveMining = true;
    public boolean perfectPlacement = true;
    public boolean rotateSlab = true;
    public boolean preventMovement = true;

    private static BetterSlabConfig instance;

    public static BetterSlabConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = new FileReader(CONFIG_PATH.toFile())) {
                    instance = GSON.fromJson(reader, BetterSlabConfig.class);
                }
            } else {
                instance = new BetterSlabConfig();
                save();
            }
        } catch (Exception e) {
            BetterSlab.LOGGER.error("Failed to load BetterSlab config, using defaults", e);
            instance = new BetterSlabConfig();
        }
        if (instance == null) {
            instance = new BetterSlabConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(instance != null ? instance : new BetterSlabConfig(), writer);
            }
        } catch (Exception e) {
            BetterSlab.LOGGER.error("Failed to save BetterSlab config", e);
        }
    }
}
