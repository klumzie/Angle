package me.contaria.anglesnap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import me.contaria.anglesnap.AngleEntry;
import me.contaria.anglesnap.AngleSnap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

public class AngleSnapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("anglesnap");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("anglesnap.json");
    private static final Path OLD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("anglesnap.json");

    private final Map<String, Option<?>> options;

    public final BooleanOption angleHud;
    public final BooleanOption renderOverlays;
    public final FloatOption markerScale;
    public final FloatOption textScale;
    public final BooleanOption snapToAngle;
    public final FloatOption snapDelay;
    public final FloatOption snapLock;
    public final FloatOption snapDistance;

    @Nullable
    private Path anglesPath;
    @Nullable
    private List<AngleEntry> angles;

    public AngleSnapConfig() {
        this.options = new LinkedHashMap<>();
        this.renderOverlays = this.register("renderOverlays", true);
        this.angleHud = this.register("angleHud", true);
        this.markerScale = this.register("markerScale", 0.0f, 1.0f, 0.2f);
        this.textScale = this.register("textScale", 0.0f, 1.0f, 0.2f);
        this.snapToAngle = this.register("snapToAngle", false);
        this.snapDelay = this.register("snapDelay", 0.0f, 1.0f, 0.0f);
        this.snapLock = this.register("snapLock", 0.0f, 1.0f, 0.25f);
        this.snapDistance = this.register("snapDistance", 0.0f, 10.0f, 2.5f);
        this.load();
        this.save();
    }

    private BooleanOption register(String id, boolean defaultValue) {
        return this.register(new BooleanOption(id, defaultValue));
    }

    private FloatOption register(String id, float min, float max, float defaultValue) {
        return this.register(new FloatOption(id, min, max, defaultValue));
    }

    private <T extends Option<?>> T register(T option) {
        if(this.options == null){
            throw new IllegalStateException("Option failed to initialize!");
        }
        if ( this.options.put(option.getId(), option) != null) {
            throw new IllegalStateException("Tried to register option '" + option.getId() + "' twice!");
        }
        return option;
    }

    public void loadAngles(String name, boolean multiplayer) {
        this.anglesPath = this.resolveDirectory(name, multiplayer).resolve("angles.json");
        this.loadAngles();
        this.saveAngles();
    }

    private Path resolveDirectory(String name, boolean multiplayer) {
        Path path = CONFIG_DIR.resolve(multiplayer ? "multiplayer" : "singleplayer");
        try {
            return path.resolve(name);
        } catch (InvalidPathException e) {
            return path.resolve(name.replaceAll("[^a-zA-Z0-9-_. ]", "_"));
        }
    }

    public void unloadAngles() {
        this.saveAngles();
        this.anglesPath = null;
        this.angles = null;
    }

    private void loadAngles() {
        if (this.anglesPath == null) {
            return;
        }
        AngleSnap.LOGGER.info("[AngleSnap] Loading angles file...");
        try {
            if (Files.exists(this.anglesPath)) {
                try (JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(this.anglesPath, StandardCharsets.UTF_8))) {
                    this.angles = AngleEntry.listFromJson(GSON.fromJson(reader, JsonObject.class));
                }
            } else {
                this.angles = new ArrayList<>();
            }
        } catch (Exception e) {
            AngleSnap.LOGGER.error("[AngleSnap] Failed to read angles file at '{}'!", this.anglesPath, e);
            this.angles = new ArrayList<>();
        }
    }

    public void saveAngles() {
        if (this.anglesPath == null || this.angles == null) {
            return;
        }
        AngleSnap.LOGGER.info("[AngleSnap] Writing angles file...");
        try {
            if (this.angles.isEmpty()) {
                Files.deleteIfExists(this.anglesPath);
                Files.deleteIfExists(this.anglesPath.getParent());
            } else {
                Files.createDirectories(this.anglesPath.getParent());
                Files.writeString(this.anglesPath, GSON.toJson(AngleEntry.listToJson(this.angles)));
            }
        } catch (Exception e) {
            AngleSnap.LOGGER.error("[AngleSnap] Failed to write angles file at '{}'!", this.anglesPath, e);
        }
    }

    public void load() {
        AngleSnap.LOGGER.info("[AngleSnap] Loading config file...");
        try {
            if (Files.exists(OLD_CONFIG_PATH)) {
                if (!Files.exists(CONFIG_PATH)) {
                    Files.createDirectories(CONFIG_DIR);
                    Files.copy(OLD_CONFIG_PATH, CONFIG_PATH);
                }
                Files.delete(OLD_CONFIG_PATH);
            }
            if (Files.exists(CONFIG_PATH)) {
                try (JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8))) {
                    this.fromJson(GSON.fromJson(reader, JsonObject.class));
                }
            }
        } catch (Exception e) {
            AngleSnap.LOGGER.error("[AngleSnap] Failed to read config file!", e);
        }
    }

    private void fromJson(JsonObject jsonObject) {
        for (String key : jsonObject.keySet()) {
            if (this.options.containsKey(key)) {
                this.options.get(key).fromJson(jsonObject.get(key));
            }
        }
    }

    public void save() {
        AngleSnap.LOGGER.info("[AngleSnap] Writing config file...");
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, GSON.toJson(this.toJson()));
        } catch (Exception e) {
            AngleSnap.LOGGER.error("[AngleSnap] Failed to write config file!", e);
        }
    }

    private JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        for (Option<?> option : this.options.values()) {
            jsonObject.add(option.getId(), option.toJson());
        }
        return jsonObject;
    }

    public boolean hasAngles() {
        return this.angles != null;
    }

    public List<AngleEntry> getAngles() {
        return this.angles != null ? Collections.unmodifiableList(this.angles) : Collections.emptyList();
    }

    public void removeAngle(AngleEntry angle) {
        if (this.angles == null) {
            AngleSnap.LOGGER.warn("[AngleSnap] Tried to remove angle but no angles are currently loaded!");
            return;
        }
        this.angles.remove(angle);
    }

    public AngleEntry createAngle() {
        if (this.angles == null) {
            AngleSnap.LOGGER.warn("[AngleSnap] Tried to create angle but no angles are currently loaded!");
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        AngleEntry angle = new AngleEntry(
                player != null ? (int) (MathHelper.wrapDegrees(player.getYaw()) * 10.0f) / 10.0f : 0.0f,
                player != null ? (int) (MathHelper.wrapDegrees(player.getPitch()) * 10.0f) / 10.0f : 0.0f
        );
        this.angles.add(angle);
        return angle;
    }

    public Iterable<Option<?>> getOptions() {
        return this.options.values();
    }
}
