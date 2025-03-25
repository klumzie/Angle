package me.contaria.anglesnap;

import com.mojang.logging.LogUtils;
import me.contaria.anglesnap.config.AngleSnapConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Objects;

public class AngleSnap implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final AngleSnapConfig CONFIG = new AngleSnapConfig();
    private static double lastTickPosX,lastTickPosY,lastTickPosZ = 0;
    public static KeyBinding openMenu;
    public static KeyBinding openOverlay;
    private static boolean wasToggleOverlayPressed = false;

    @Override
    public void onInitializeClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.openmenu",
                GLFW.GLFW_KEY_F6,
                "anglesnap.key"
        ));
        openOverlay = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.openoverlay",
                GLFW.GLFW_KEY_F7,
                "anglesnap.key"
        ));

        WorldRenderEvents.LAST.register(ctx -> AngleSnap.renderOverlay(ctx.matrixStack()));
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(IdentifiedLayer.DEBUG, Identifier.of("anglesnap", "overlay"), AngleSnap::renderHud));

        ClientPlayConnectionEvents.JOIN.register((networkHandler, packetSender, client) -> {
            if (client.isIntegratedServerRunning()) {
                AngleSnap.CONFIG.loadAngles(Objects.requireNonNull(client.getServer()).getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString(), false);
            } else {
                AngleSnap.CONFIG.loadAngles(Objects.requireNonNull(networkHandler.getServerInfo()).address, true);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> AngleSnap.CONFIG.unloadAngles());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isKeyDown = openOverlay.isPressed();
            if (isKeyDown && !wasToggleOverlayPressed) {
                if (client.player != null) {
                    AngleSnap.CONFIG.renderOverlays.setValue(!AngleSnap.CONFIG.renderOverlays.getValue());
                    LOGGER.info("Overlay toggled: " + AngleSnap.CONFIG.renderOverlays.getValue());
                    AngleSnap.CONFIG.save();
                    AngleSnap.CONFIG.load();
                }
            }
            wasToggleOverlayPressed = isKeyDown;
        });
    }

    public static boolean shouldRenderOverlay() {
        return AngleSnap.CONFIG.renderOverlays.getValue();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (shouldRenderOverlay()) {
            if (AngleSnap.CONFIG.angleHud.getValue()) {
                renderAngleHud(context);
            }
        }
    }

    public static void renderOverlay(MatrixStack matrixStack) {
        if (shouldRenderOverlay()) {
            for (AngleEntry angle : AngleSnap.CONFIG.getAngles()) {
                renderMarker(matrixStack, angle, AngleSnap.CONFIG.markerScale.getValue(), AngleSnap.CONFIG.textScale.getValue());
            }
        }
    }

    private static void renderMarker(MatrixStack matrixStack, AngleEntry angle, float markerScale, float textScale) {
        markerScale = markerScale / 1.5f;
        textScale = textScale / 4;
        MinecraftClient client = MinecraftClient.getInstance();

        Vec3d camPos = client.cameraEntity.getEyePos();



        Vec3d pos = Vec3d.fromPolar(
                MathHelper.wrapDegrees(angle.pitch),
                MathHelper.wrapDegrees(angle.yaw + 180.0f)
        ).multiply(-10.0, 10.0, -10.0);
        Vec3d worldPos = camPos.add(pos.x, pos.y, pos.z);
        Quaternionf rotation = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();

        lastTickPosX = camPos.getX();
        lastTickPosY = camPos.getY();
        lastTickPosZ = camPos.getZ();

        float x = (float) (worldPos.getX() - MathHelper.lerp(0, lastTickPosX, camPos.getX()));
        float y = (float) (worldPos.getY() - MathHelper.lerp(0, lastTickPosY, camPos.getY()));
        float z = (float) (worldPos.getZ() - MathHelper.lerp(0, lastTickPosZ, camPos.getZ()));

        matrixStack.push();
        matrixStack.translate(x, y, z);
        drawIcon(matrixStack, pos, rotation, angle, markerScale);
        if (!angle.name.isEmpty()) {
            drawName(matrixStack,angle.color, pos, rotation, angle, textScale);
        }
        matrixStack.pop();
    }

    private static void drawIcon(MatrixStack matrixStack, Vec3d pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f) {
            return;
        }

        MatrixStack matrices = Objects.requireNonNull(matrixStack);
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(rotation);
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        RenderLayer layer = RenderLayer.getGuiTexturedOverlay(angle.getIcon());

        VertexConsumerProvider.Immediate immediate = getVertexConsumer();

        VertexConsumer consumer = immediate.getBuffer(layer);
        consumer.vertex(matrix4f, -1.0f, -1.0f, 0.0f).color(angle.color).texture(0.0f, 0.0f);
        consumer.vertex(matrix4f, -1.0f, 1.0f, 0.0f).color(angle.color).texture(0.0f, 1.0f);
        consumer.vertex(matrix4f, 1.0f, 1.0f, 0.0f).color(angle.color).texture(1.0f, 1.0f);
        consumer.vertex(matrix4f, 1.0f, -1.0f, 0.0f).color(angle.color).texture(1.0f, 0.0f);

        matrices.scale(1.0f / scale, 1.0f / -scale, 1.0f / scale);
        matrices.pop();
    }
    private static VertexConsumerProvider.Immediate getVertexConsumer() {
        return MinecraftClient.getInstance().getBufferBuilders().getEffectVertexConsumers();
    }

    private static void drawName(MatrixStack matrixStack, int markerColor, Vec3d pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f || angle.name.isEmpty()) {
            return;
        }

        MatrixStack matrices = Objects.requireNonNull(matrixStack);
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(rotation);
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate immediate = getVertexConsumer();
        TextRenderer textRenderer = client.textRenderer;
        float x = -textRenderer.getWidth(angle.name) / 2.0f;
        int backgroundColor = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
        textRenderer.draw(
                angle.name, x, -15.0f, markerColor, false, matrix4f, immediate, TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, LightmapTextureManager.MAX_LIGHT_COORDINATE
        );

        matrices.scale(1.0f / scale, 1.0f / -scale, 1.0f / scale);
        matrices.pop();
    }

    private static void renderAngleHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud() || client.player == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        String text = String.format("%.3f / %.3f", MathHelper.wrapDegrees(client.player.getYaw()), MathHelper.wrapDegrees(client.player.getPitch()));
        context.fill(5, 5, 5 + 2 + textRenderer.getWidth(text) + 2, 5 + 2 + textRenderer.fontHeight + 2, -1873784752);
        context.drawText(textRenderer, text, 5 + 2 + 1, 5 + 2 + 1, 14737632, false);
    }
}
