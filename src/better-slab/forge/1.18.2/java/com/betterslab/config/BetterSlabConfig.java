package com.betterslab.config;

import com.betterslab.BetterSlab;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置文件。
 * 配置文件位于 config/betterslab.json，只包含功能开关。
 * 按键绑定通过 Forge RegisterKeyMappingsEvent 注册，可在游戏设置中修改。
 */
public class BetterSlabConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path CONFIG_PATH;

    public boolean verticalSlab = true;
    public boolean selectiveMining = true;
    public boolean perfectPlacement = true;
    public boolean rotateSlab = true;
    public boolean preventMovement = true;

    private static BetterSlabConfig instance;

    private static Path configPath() {
        if (CONFIG_PATH == null) {
            CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("betterslab.json");
        }
        return CONFIG_PATH;
    }

    public static BetterSlabConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                try (var reader = new FileReader(path.toFile())) {
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
            Path path = configPath();
            Files.createDirectories(path.getParent());
            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(instance != null ? instance : new BetterSlabConfig(), writer);
            }
        } catch (Exception e) {
            BetterSlab.LOGGER.error("Failed to save BetterSlab config", e);
        }
    }
}
