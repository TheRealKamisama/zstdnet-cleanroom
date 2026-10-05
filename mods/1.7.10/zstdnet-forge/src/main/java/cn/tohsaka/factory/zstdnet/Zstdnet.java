package cn.tohsaka.factory.zstdnet;

import cn.tohsaka.factory.zstdnet.network.ReportNetwork;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(modid = Zstdnet.MODID, name = "ZstdNet", version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class Zstdnet {
    public static final String MODID = "zstdnet";
    @SidedProxy(clientSide = "cn.tohsaka.factory.zstdnet.ClientProxy", serverSide = "cn.tohsaka.factory.zstdnet.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        Platform.configure(event.getModConfigurationDirectory().toPath(), FMLCommonHandler.instance().getSide().isClient());
        proxy.preInit();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        ReportNetwork.init();
        ServerProxyBootstrap.init();
        proxy.init();
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) { ServerProxyBootstrap.started(); }
    @Mod.EventHandler public void stopping(FMLServerStoppingEvent event) { ServerProxyBootstrap.stop(); }
}