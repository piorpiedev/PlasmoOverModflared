package com.piorpie.pom;

import java.io.*;
import java.net.*;

public class Udp2Tcp implements AutoCloseable {
    private final DatagramSocket udpSocket;
    private final Socket tcpSocket;
    private volatile SocketAddress udpSocketAddress;
    private final InetSocketAddress tcpSocketAddr;

    private volatile boolean closed = false;

    public Udp2Tcp(int udpPort, InetSocketAddress tcpSocketAddr, int timeout) throws SocketException {
        this.tcpSocketAddr = tcpSocketAddr;
        tcpSocket = new Socket();
        tcpSocket.setTcpNoDelay(true); // Disable Nagle's algorithm
        tcpSocket.setSoTimeout(timeout);

        this.udpSocket = new DatagramSocket(udpPort);
    }
    public Udp2Tcp(int udpPort, InetSocketAddress tcpSocketAddr) throws SocketException {
        this(udpPort, tcpSocketAddr, 60_000);
    }

    public void run() {
        Thread.ofVirtual().name("udp2tcp-rx").start(() -> {
            // Wait for first packet to get the socket address
            try {
                tcpSocket.connect(tcpSocketAddr);
                byte[] buff = new byte[65535];

                DatagramPacket packet = new DatagramPacket(buff, buff.length);
                udpSocket.receive(packet);
                udpSocketAddress = packet.getSocketAddress();

                Thread.ofVirtual().name("udp2tcp-tx").start(() -> startTx(packet, buff));
            } catch (IOException e) {
                close();
                throw new RuntimeException(e);
            }

            startRx(); // Reuse the same thread
        });
    }

    private void startTx(DatagramPacket firstPacket, byte[] buff) {
        DataOutputStream tcpOut;
        try {
            tcpOut = new DataOutputStream(new BufferedOutputStream(tcpSocket.getOutputStream(), 65535));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            DatagramPacket packet = firstPacket;
            while (canTransmit()) {
                tcpOut.writeShort(packet.getLength()); // Mullvad's udp-over-tcp packets have the packet length set as header
                tcpOut.write(packet.getData(), packet.getOffset(), packet.getLength());
                tcpOut.flush();

                packet = new DatagramPacket(buff, buff.length);
                udpSocket.receive(packet);
                udpSocketAddress = packet.getSocketAddress();
            }
        } catch (IOException e) {
            if (canTransmit()) throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    private void startRx() {
        DataInputStream tcpIn;
        try {
            tcpIn = new DataInputStream(tcpSocket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            while (canTransmit()) {
                int length = tcpIn.readUnsignedShort();
                byte[] data = tcpIn.readNBytes(length);
                if (data.length < length) break; // EOF

                udpSocket.send(new DatagramPacket(data, data.length, udpSocketAddress));
            }
        } catch (EOFException | SocketTimeoutException ignored) {
            // Connection closed
        } catch (IOException e) {
            if (canTransmit()) throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (!udpSocket.isClosed()) udpSocket.close();
        if (!tcpSocket.isClosed()) {
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
    public boolean isClosed() {
        return closed;
    }

    private boolean canTransmit() {
        return !closed && !udpSocket.isClosed() && !tcpSocket.isClosed();
    }
}
