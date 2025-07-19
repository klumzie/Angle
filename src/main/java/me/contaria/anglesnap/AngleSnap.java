package me.contaria.anglesnap;

import com.mojang.logging.LogUtils;
import me.contaria.anglesnap.config.AngleSnapConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.Objects;

public class AngleSnap implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final AngleSnapConfig CONFIG = new AngleSnapConfig();

    public static KeyBinding openMenu;
    public static KeyBinding openOverlay;
    public static KeyBinding cameraPositions;

    @Nullable
    public static CameraPosEntry currentCameraPos;

    @Override
    public void onInitializeClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.openmenu",
                GLFW.GLFW_KEY_F6,
                "anglesnap.key"
        ));
        openOverlay = KeyBindingHelper.registerKeyBinding(new StickyKeyBinding(
                "anglesnap.key.openoverlay",
                GLFW.GLFW_KEY_F7,
                "anglesnap.key",
                () -> true
        ));
        cameraPositions = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.camerapositions",
                GLFW.GLFW_KEY_F8,
                "anglesnap.key"
        ));

        // Use the modern WorldRenderEvents and HudRenderCallback
        WorldRenderEvents.LAST.register(AngleSnap::renderOverlay);
        HudRenderCallback.EVENT.register(AngleSnap::renderHud);

        // This handles opening the configuration screens since they are not Tick-based
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.wasPressed()) {
                client.setScreen(new AngleConfigScreen());
            }
            while (cameraPositions.wasPressed()) {
                client.setScreen(new CameraPositionConfigScreen());
            }
        });

        ClientPlayConnectionEvents.JOIN.register((networkHandler, packetSender, client) -> {
            if (client.isIntegratedServerRunning()) {
                AngleSnap.CONFIG.loadAnglesAndCameraPositions(Objects.requireNonNull(client.getServer()).getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString(), false);
            } else {
                AngleSnap.CONFIG.loadAnglesAndCameraPositions(Objects.requireNonNull(networkHandler.getServerInfo()).address, true);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> AngleSnap.CONFIG.unloadAnglesAndCameraPositions());
    }

    public static boolean shouldRenderOverlay() {
        return openOverlay.isPressed();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (shouldRenderOverlay()) {
            if (AngleSnap.CONFIG.angleHud.getValue()) {
                renderAngleHud(context);
            }
        }
    }

    private static void renderOverlay(WorldRenderContext context) {
        if (shouldRenderOverlay()) {
            for (AngleEntry angle : AngleSnap.CONFIG.getAngles()) {
                renderMarker(context, angle, AngleSnap.CONFIG.markerScale.getValue(), AngleSnap.CONFIG.textScale.getValue());
            }
        }
    }

    private static void renderMarker(WorldRenderContext context, AngleEntry angle, float markerScale, float textScale) {
        markerScale = markerScale / 10.0f;
        textScale = textScale / 50.0f;

        // Note: fromPolar yaw is counter-clockwise, Minecraft yaw is clockwise.
        // Adding 180 to yaw and negating the z-axis component flips it correctly.
        Vector3f pos = Vec3d.fromPolar(
                angle.pitch,
                angle.yaw
        ).multiply(1.0, -1.0, 1.0).toVector3f();

        Quaternionf rotation = context.camera().getRotation();
        // Create a copy and invert it to make the marker face the camera
        Quaternionf inverseRotation = new Quaternionf(rotation).conjugate();

        drawIcon(context, pos, inverseRotation, angle, markerScale);
        if (angle.name != null && !angle.name.isEmpty()) {
            drawName(context, pos, inverseRotation, angle, textScale);
        }
    }

    private static void drawIcon(WorldRenderContext context, Vector3f pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        // Move to the camera's position then to the marker's relative position
        Vec3d cameraPos = context.camera().getPos();
        matrices.translate(pos.x() - cameraPos.x, pos.y() - cameraPos.y, pos.z() - cameraPos.z);
        matrices.multiply(rotation);
        matrices.scale(scale, scale, scale);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        // Use the in-game effect buffer builder for rendering in the world
        VertexConsumer consumer = MinecraftClient.getInstance().getBufferBuilders().getEffectVertexConsumers().getBuffer(RenderLayer.getText(angle.getIcon()));
        
        consumer.vertex(matrix4f, -8f, -8f, 0f).color(angle.color).texture(0f, 0f).light(15728880);
        consumer.vertex(matrix4f, -8f, 8f, 0f).color(angle.color).texture(0f, 1f).light(15728880);
        consumer.vertex(matrix4f, 8f, 8f, 0f).color(angle.color).texture(1f, 1f).light(15728880);
        consumer.vertex(matrix4f, 8f, -8f, 0f).color(angle.color).texture(1f, 0f).light(15728880);

        matrices.pop();
    }

    private static void drawName(WorldRenderContext context, Vector3f pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f || angle.name == null || angle.name.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        Vec3d cameraPos = context.camera().getPos();
        matrices.translate(pos.x() - cameraPos.x, pos.y() - cameraPos.y, pos.z() - cameraPos.z);
        matrices.multiply(rotation);
        matrices.scale(scale, -scale, scale);

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        float x = -textRenderer.getWidth(angle.name) / 2.0f;
        // Position the text below the icon
        float y = 10.0f / scale; 
        
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        int backgroundColor = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
        
        textRenderer.draw(
                Text.of(angle.name), x, y, Colors.WHITE, false, matrix4f, client.getBufferBuilders().getEffectVertexConsumers(), TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, 15728880
        );

        matrices.pop();
    }

    private static void renderAngleHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud() || client.player == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        String text = String.format("%.3f / %.3f", MathHelper.wrapDegrees(client.player.getYaw()), MathHelper.wrapDegrees(client.player.getPitch()));
        int textWidth = textRenderer.getWidth(text);
        
        context.drawTextWithShadow(textRenderer, text, 5, 5, 14737632);
    }

    public static boolean isInMultiplayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && !client.isInSingleplayer();
    }
}
