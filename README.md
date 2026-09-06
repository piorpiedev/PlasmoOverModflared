Allows users to connect to a Plasmo Voice channel over Cloudflare tunnels

## Wait, how
Cloudflare tunnels don't allow udp connections, even if you use cloudflared to connect

This mod runs a java implementation of Mullvad's [udp-over-tcp](https://github.com/mullvad/udp-over-tcp) udp2tcp client, spawning a "tunnel" when Plasmo connects to a server which wraps the udp datagrams in tcp packets, before forwarding them (inbound) to a cloudflared binary managed by Modflared. Yes, it's a lot

TCP_NODELAY is enabled to avoid buffering (just like in Mullvad's rust implementation)

## Client Installation
If you are a player of the server, simply install the mod and the dependencies (Plasmo Voice and Modflared, and you are good to go)

## Server Installation
Add the prefix `MODFLARED:` (case insensitive) to the ip under the [host.public] section, in the serverside Plasmo Voice config. Choose an arbitrary port or leave 0 to use the same port as your server (default)

Make sure to also set said ip to the Cloudflare tunnel's domain (which should be different from the server's public domain)

You will also need an instance of the original [binary tcp2udp](https://github.com/mullvad/udp-over-tcp) running on the server. As of writing, pre-built releases for it are not available, so you will have to compile the binary yourself, by running `cargo build --release`, after cloning the repository or running the `build-static-bins.sh`. It's simpler than it sounds like

You will then run the tcp2udp with arguments `--tcp-listen 0.0.0.0:{PLASMO_PUBLIC_PORT} --udp-forward 127.0.0.1:{PLASMO_SERVER_PORT}`. Said server ports are the ones under [host] and [host.public], respectively. I also suggest running with `RUST_LOG=debug` as an environment variable. The addresses need to be numeric ips, hostnames are not supported. If you set Plasmo port to 0 in the config, use instead the same port as the Minecraft server

No need to install any mod on the server (apart from Plasmo Voice), so this will work on any server architecture that is supported by Plasmo Voice (Proxies and Spigot forks included). This doesn't even need to run on the same server as the Minecraft server

> I do expect you to know how to set up a Cloudflare tunnel if you have gotten this far into reading how to install the mod. If you don't, you can find more information about it in the [Modflared mod page](/mod/modflared)
