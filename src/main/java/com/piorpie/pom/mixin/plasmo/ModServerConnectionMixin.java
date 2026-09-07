package com.piorpie.pom.mixin.plasmo;

import com.piorpie.pom.PlasmoOverModflared;
import com.piorpie.pom.Udp2Tcp;
import com.piorpie.pom.mixin.modflared.TunnelManagerAccessor;
import dev.httxrafa.modflared.Modflared;
import dev.httxrafa.modflared.tunnel.RunningTunnel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.voice.api.client.socket.UdpClient;
import su.plo.voice.client.connection.ModServerConnection;

import java.net.InetSocketAddress;
import java.net.SocketException;

@Mixin(value = ModServerConnection.class, remap = false)
public class ModServerConnectionMixin {
    @Unique
    private RunningTunnel tunnel = null;
    @Unique
    private Udp2Tcp udp2tcp = null;

    @Redirect(method = "handle(Lsu/plo/voice/proto/packets/tcp/clientbound/ConnectionPacket;)V", at = @At(value = "INVOKE", target = "Lsu/plo/voice/api/client/socket/UdpClient;connect(Ljava/lang/String;I)V"))
    private void redirectUdpClientConnect(UdpClient client, String udpHost, int udpPort) {
        PlasmoOverModflared.LOGGER.debug("[PlasmoOverModflared] Requested connection to: {}:{}", udpHost, udpPort);
        final String MODFLARED_PREFIX = "modflared:";
        if (!udpHost.toLowerCase().startsWith(MODFLARED_PREFIX)) {
            client.connect(udpHost, udpPort);
            return;
        }
        // Close previous tunnels if any
        if (tunnel != null || udp2tcp != null) closeTunnel();

        // Open cloudflared tunnel
        String target = udpHost.substring(MODFLARED_PREFIX.length()).stripLeading();
        PlasmoOverModflared.LOGGER.info("[PlasmoOverModflared] Connecting to tunnel: {}", target);
        waitForModflared();
        tunnel = Modflared.TUNNEL_MANAGER.createTunnel(target);
        if (tunnel == null) {
            PlasmoOverModflared.LOGGER.error("[PlasmoOverModflared] Unable to create cloudflared tunnel");
            return;
        }

        // Start Udp2Tcp client
        int tunnelPort = tunnel.access().tunnelAddress().getPort();
        PlasmoOverModflared.LOGGER.debug("[PlasmoOverModflared] Starting udp2tcp client");
        try {
            this.udp2tcp = new Udp2Tcp(udpPort, new InetSocketAddress("127.0.0.1", tunnelPort));
        } catch (SocketException e) {
            closeTunnel();
            throw new RuntimeException(e);
        }
        udp2tcp.run();

        // Connect to plasmo voice server
        client.connect("127.0.0.1", udpPort);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void closeTunnel(CallbackInfo ci) {
        PlasmoOverModflared.LOGGER.debug("[PlasmoOverModflared] Gracefully closing connection");
        closeTunnel();
    }

    @Unique
    private void closeTunnel() {
        if (tunnel != null) {
            PlasmoOverModflared.LOGGER.info("[PlasmoOverModflared] Closing cloudflared tunnel");
            Modflared.TUNNEL_MANAGER.closeTunnel(tunnel);
            tunnel = null;
        }
        if (udp2tcp != null) {
            PlasmoOverModflared.LOGGER.info("[PlasmoOverModflared] Closing udp2tcp tunnel");
            udp2tcp.close();
            udp2tcp = null;
        }
    }

    @Unique
    private void waitForModflared() {
        int timeoutMs = 5000;
        int waited = 0;

        while (((TunnelManagerAccessor) Modflared.TUNNEL_MANAGER).getCloudflared().get() == null && waited < timeoutMs) {
            try {
                Thread.sleep(100);
                waited += 100;
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
