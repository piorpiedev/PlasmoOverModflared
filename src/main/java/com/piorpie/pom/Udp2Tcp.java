package com.piorpie.pom;

import org.jspecify.annotations.NonNull;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Udp2Tcp implements AutoCloseable {
    private final DatagramSocket udpSocket;
    private final InetSocketAddress tcpSocketAddr;
    private final int maxQueueCapacity;

    private final ConcurrentHashMap<SocketAddress, PeerConnection> peers = new ConcurrentHashMap<>();

    public Udp2Tcp(int udpPort, InetSocketAddress tcpSocketAddr, int maxQueueCapacity) throws SocketException {
        this.udpSocket = new DatagramSocket(udpPort);
        this.tcpSocketAddr = tcpSocketAddr;
        this.maxQueueCapacity = maxQueueCapacity;
    }

    private static class PeerConnection implements AutoCloseable {
        private final BlockingQueue<byte[]> txQueue;
        private volatile Socket tcpSocket = null;
        private volatile boolean closed = false;

        private PeerConnection(int maxQueueCapacity) {
            this.txQueue = new ArrayBlockingQueue<>(maxQueueCapacity);
        }

        private void attachSocket(Socket socket) {
            this.tcpSocket = socket;
            if (closed) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        public boolean send(byte[] data) {
            if (closed) return false;
            return txQueue.offer(data);
        }

        public byte[] poll(long timeout) throws InterruptedException {
            return txQueue.poll(timeout, TimeUnit.MILLISECONDS);
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                try {
                    tcpSocket.close();
                } catch (IOException ignored) {
                }
            }
            txQueue.clear();
        }
    }

    public void run() {
        Thread.ofVirtual().name("udp2tcp-tunnel").start(() -> {
            byte[] buff = new byte[65535];

            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buff, buff.length);
                    udpSocket.receive(packet);

                    SocketAddress peerAddr = packet.getSocketAddress();
                    byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                    PeerConnection peer = peers.compute(peerAddr, (addr, conn) -> {
                        if (conn != null && !conn.isClosed()) return conn;
                        return createPeerTask(addr);
                    });
                    var _ = peer.send(data); // If the queue is full, don't forward the new packets
                } catch (IOException e) {
                    if (!udpSocket.isClosed()) throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public void close() {
        if (!udpSocket.isClosed()) {
            udpSocket.close();
        }
        peers.forEach((_, conn) -> conn.close());
        peers.clear();
    }

    // Connection handler
    private @NonNull PeerConnection createPeerTask(SocketAddress addr) {
        PeerConnection peer = new PeerConnection(maxQueueCapacity);

        Thread.ofVirtual().name("udp2tcp-worker-tx").start(() -> {
            try (Socket tcpSocket = new Socket()) {
                tcpSocket.setTcpNoDelay(true); // Disable Nagle's algorithm
                tcpSocket.setSoTimeout(60_000);

                // Connect
                tcpSocket.connect(tcpSocketAddr);
                peer.attachSocket(tcpSocket);
                DataOutputStream tcpOut = new DataOutputStream(new BufferedOutputStream(tcpSocket.getOutputStream(), 65535));
                DataInputStream tcpIn = new DataInputStream(tcpSocket.getInputStream());

                // TCP -> UDP
                Thread.ofVirtual().name("udp2tcp-worker-rx").start(() -> {
                    try {
                        while (!tcpSocket.isClosed()) {
                            int len = tcpIn.readUnsignedShort(); // Read Mullvad's udp-over-tcp header
                            byte[] data = tcpIn.readNBytes(len);
                            if (data.length < len) break; // EOF (connection closed)

                            udpSocket.send(new DatagramPacket(data, data.length, addr));
                        }
                    } catch (EOFException | SocketTimeoutException ignored) {
                        // Disconnected
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        peer.close();
                        peers.remove(addr, peer);
                    }
                });

                // UDP -> TCP
                while (!tcpSocket.isClosed()) {
                    byte[] data = peer.poll(500);
                    if (data == null) continue;
                    tcpOut.writeShort(data.length); // Write Mullvad's udp-over-tcp header
                    tcpOut.write(data);
                    tcpOut.flush();
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        return peer;
    }
}
