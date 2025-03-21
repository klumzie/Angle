package me.contaria.anglesnap.gui.screen;

import me.contaria.anglesnap.AngleEntry;
import me.contaria.anglesnap.AngleSnap;
import me.contaria.anglesnap.gui.config.AngleSnapConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AngleSnapScreen extends Screen {
    private static final Text CONFIGURE_TEXT = Text.translatable("anglesnap.gui.screen.configure");
    private static final Identifier CONFIGURE_TEXTURE = Identifier.of("anglesnap", "textures/gui/configure.png");
    private Screen parent;

    public AngleSnapScreen(Screen parent) {
        super(Text.translatable("anglesnap.gui.screen.title"));
        if (parent != null) {
            this.parent = parent;
        }
    }

    @Override
    protected void init() {
        this.addDrawableChild(new TextWidget(0, 10, this.width, 15, this.title, this.textRenderer).alignCenter());
        this.addDrawableChild(new IconButtonWidget(this.width - 26, 10, CONFIGURE_TEXT, button -> MinecraftClient.getInstance().setScreen(new AngleSnapConfigScreen(this)), CONFIGURE_TEXTURE));
        this.addDrawableChild(new AngleSnapListWidget(this.client, this.width, this.height - 70, 35, this));
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.width / 2 - 100, this.height - 27, 200, 20).build());
    }

    public void snap(AngleEntry angle) {
        angle.snap();
        this.close();
    }

    @Override
    public void close() {
        if (this.parent != null) {
            MinecraftClient.getInstance().setScreen(this.parent);
        }
        else {
            super.close();
        }
    }

    @Override
    public void removed() {
        AngleSnap.CONFIG.save();
    }
}
