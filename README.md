# PlasmoOverModflared

Allows users to connect to a Plasmo Voice channel over Cloudflare tunnels

## Wait, how
Cloudflare tunnels don't allow UDP connections, even if you use cloudflared to connect

This mod uses a Java implementation of Mullvad's [udp-over-tcp](https://github.com/mullvad/udp-over-tcp) udp2tcp client, spawning a tunnel that forwards the content of UDP datagrams sent by Plasmo as TCP packets, before forwarding them (inbound) to a cloudflared binary, managed by Modflared

TCP_NODELAY is enabled to limit buffering (just like in Mullvad's Rust implementation)

## Client Installation
If you're a player, simply install the mod and the dependencies (Plasmo Voice, Modflared, and the Fabric API), and you are good to go

## Server Installation
In the \[host.public] section of the serverside Plasmo Voice config, set the "ip" value to `MODFLARED:` (case insensitive), followed by your Cloudflare tunnel's domain (i.e. the tunnel you created for Plasmo, which must be different from the Minecraft server's tunnel). Choose an arbitrary port, or leave it set to 0 to use the same port as your Minecraft server (default). Plasmo uses UDP ports instead of TCP ones, so it will not cause a conflict

You will also need an instance of the original [tcp2udp binary](https://github.com/mullvad/udp-over-tcp) running on the server. As of writing, pre-built releases are not available (not by Mullvad at least), so you will have to compile the binary yourself, by:
1) Installing Cargo if you haven't already (check out [rustup](https://rustup.rs/));
2) Cloning their repository; and
3) Either running `cargo build --release`, or the `build-static-bins.sh` script

Trust me, it's not as hard as it sounds. 

NOTE: The script creates a statically linked executable, but requires more dependencies to be installed when you try to compile it

---

After compiling the binary, you need to execute it with arguments `--tcp-listen 0.0.0.0:{PLASMO_PUBLIC_PORT} --udp-forward 127.0.0.1:{PLASMO_SERVER_PORT}`. Said ports are the ones under \[host.public] and \[host], in the Plasmo serverside config from before, respectively. I also suggest running it with `RUST_LOG=debug` set as an environment variable (might have no effect if you didn't compile it with the `build-static-bins.sh` script). If you set the Plasmo Voice port to 0 in the config, use instead the same port as the Minecraft server

You do not need to use `0.0.0.0` and `127.0.0.1` if you want to bind the tunnel to a specific IP, or forward traffic to a different host, however please note that the addresses need to be numeric IPs, as hostnames are not supported

You don't need to install any mod on the server (apart from Plasmo Voice itself, even for another server architecture), meaning this will work even on Proxies and Spigot forks. However, make sure that the tcp2udp binary is always running, or users will not be able to connect to the voice chat. You might want to use a docker container or a system service. Examples are available in the [udp-over-tcp repository](https://github.com/mullvad/udp-over-tcp)

> I do expect you to know how to set up a Cloudflare tunnel if you have gotten this far into reading how to use this project. If you don't, you can find more information about it in the [Modflared mod page](https://modrinth.com/mod/modflared)

### Note for server owners
At no point does the mod require for the Minecraft server itself to be accessed via cloudflared. While Modflared **is** a required dependency for the players, you are free to use this mod to proxy only the Plasmo Voice channel, and expose the server in some other manner

## Credits
- Mullvad's [udp-over-tcp](https://github.com/mullvad/udp-over-tcp) project. This mod is only possible due to porting their udp2tcp client in Java, and requires the server owner to host their tcp2udp server
- [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice), for the client and server mods/plugins that this mods lets you connect to
- [Modflared](https://modrinth.com/mod/modflared), for the cloudflared tunnels manager and downloader
- [Cloudflare](https://developers.cloudflare.com/), for their amazing infrastructure and the [cloudflared](https://github.com/cloudflare/cloudflared) binary, used by Modflared and indirectly by this mod

This project is not affiliated with any of the previously mentioned credited projects and teams

## Footnote
If you are a developer, and would like to use the Java port of Mullvad's udp-over-tcp udp2tcp implementation, it is self-contained within [`com.piorpie.pom.Udp2Tcp`](https://github.com/piorpiedev/PlasmoOverModflared/blob/master/src/main/java/com/piorpie/pom/Udp2Tcp.java) in the src folder. No dependencies needed, not even netty. Make sure to credit and/or mention me/this project as well as Mullvad team/original project, as per Apache 2.0 or MIT license specifications
