package cn.tohsaka.factory.zstdnet.coremod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("ZstdNet")
public final class ZstdLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override public String getMixinConfig() { return "mixins.zstdnet.json"; }
    @Override public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> result = new ArrayList<>(List.of("HandshakeAccessor",
            "NetworkManagerAccessor", "ServerHandshakeMixin"));
        if (FMLLaunchHandler.side().isClient()) {
            result.addAll(List.of("client.GuiConnectingMixin", "client.IntegratedServerMixin", "client.GuiShareToLanMixin", "client.LanPingMixin"));
        } else {
            result.add("DedicatedServerMixin");
        }
        return result;
    }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
