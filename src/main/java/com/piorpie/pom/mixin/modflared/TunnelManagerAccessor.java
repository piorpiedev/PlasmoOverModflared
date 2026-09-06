package com.piorpie.pom.mixin.modflared;

import dev.httxrafa.modflared.binary.Cloudflared;
import dev.httxrafa.modflared.tunnel.manager.TunnelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = TunnelManager.class, remap = false)
public interface TunnelManagerAccessor {
    @Accessor("cloudflared")
    AtomicReference<Cloudflared> getCloudflared();
}
