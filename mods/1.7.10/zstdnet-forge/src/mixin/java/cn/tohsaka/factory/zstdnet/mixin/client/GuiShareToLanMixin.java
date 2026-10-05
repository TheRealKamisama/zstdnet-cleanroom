package cn.tohsaka.factory.zstdnet.mixin.client;

import cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiShareToLan.class)
public abstract class GuiShareToLanMixin extends GuiScreen {
    @Unique private GuiTextField zstdnet$game;
    @Unique private GuiTextField zstdnet$zstd;
    @Unique private String zstdnet$error = "";

    @Inject(method = "initGui", at = @At("RETURN"))
    private void zstdnet$fields(CallbackInfo ci) {
        zstdnet$game = new GuiTextField(fontRendererObj, width / 2 - 155, 145, 150, 20);
        zstdnet$zstd = new GuiTextField(fontRendererObj, width / 2 + 5, 145, 150, 20);
        zstdnet$game.setMaxStringLength(5);
        zstdnet$zstd.setMaxStringLength(5);
        zstdnet$zstd.setText(Integer.toString(ServerProxyConfigFile.readListenPort()));
        zstdnet$error = "";
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void zstdnet$draw(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        fontRendererObj.drawString(I18n.format("zstdnet.share_to_lan.backend_port"), width / 2 - 155, 132, 0xffffff);
        fontRendererObj.drawString(I18n.format("zstdnet.share_to_lan.zstd_port"), width / 2 + 5, 132, 0xffffff);
        zstdnet$game.drawTextBox();
        zstdnet$zstd.drawTextBox();
        if (!zstdnet$error.isEmpty()) drawCenteredString(fontRendererObj, zstdnet$error, width / 2, 175, 0xff7777);
    }

    @Override protected void keyTyped(char letter, int key) {
        if (key == 1) { super.keyTyped(letter, key); return; }
        if (zstdnet$game != null && zstdnet$game.textboxKeyTyped(letter, key)) return;
        if (zstdnet$zstd != null && zstdnet$zstd.textboxKeyTyped(letter, key)) return;
        super.keyTyped(letter, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (zstdnet$game != null) zstdnet$game.mouseClicked(x, y, button);
        if (zstdnet$zstd != null) zstdnet$zstd.mouseClicked(x, y, button);
    }
    @Override public void updateScreen() {
        super.updateScreen();
        if (zstdnet$game != null) zstdnet$game.updateCursorCounter();
        if (zstdnet$zstd != null) zstdnet$zstd.updateCursorCounter();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void zstdnet$savePorts(GuiButton button, CallbackInfo ci) {
        if (button.id != 101) return;
        try {
            Integer game = zstdnet$parse(zstdnet$game.getText());
            Integer relay = zstdnet$parse(zstdnet$zstd.getText());
            if (game != null && game.equals(relay)) throw new IllegalArgumentException("Ports must differ");
            ServerProxyConfigFile.writePorts(relay, game);
        } catch (IllegalArgumentException invalid) {
            zstdnet$error = I18n.format("zstdnet.share_to_lan.port_invalid");
            ci.cancel();
        } catch (Exception failure) {
            zstdnet$error = I18n.format("zstdnet.share_to_lan.write_failed");
            ci.cancel();
        }
    }
    @Unique private static Integer zstdnet$parse(String text) {
        if (text.trim().isEmpty()) return null;
        if (!text.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Not a port");
        int port = Integer.parseInt(text);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid port");
        return port;
    }
}
