package br.com.antibet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local DNS filtering prototype. It handles IPv4 UDP DNS packets only.
 * DNS-over-HTTPS/TLS, IPv6, TCP DNS and hard-coded app resolvers can bypass it.
 */
public class BlockVpnService extends VpnService {
    private static final String CHANNEL_ID = "antibet_protection";
    private static final String DNS_IP = "10.77.0.2";
    private static final String UPSTREAM_DNS = "1.1.1.1";
    private static final int DNS_PORT = 53;
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
            "betano.com", "bet365.com", "sportingbet.com", "betfair.com",
            "pixbet.com", "estrelabet.com", "novibet.com", "betnacional.com",
            "superbet.com", "kto.com", "br4bet.com", "rivalo.com",
            "casino.com", "888casino.com", "blaze.com", "stake.com",
            "betway.com", "1xbet.com", "vaidebet.com", "galerabet.com",
            "realsbet.com", "mrjack.bet", "esportesdasorte.com",
            "oddsandmore.com", "bet7k.com"
    ));

    private ParcelFileDescriptor tunnel;
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(1, buildNotification());
        if (running.compareAndSet(false, true)) {
            try {
                tunnel = new Builder()
                        .setSession("AntiBet DNS")
                        .addAddress("10.77.0.1", 30)
                        .addDnsServer(DNS_IP)
                        .addRoute(DNS_IP, 32)
                        .setMtu(1500)
                        .establish();
                if (tunnel == null) {
                    running.set(false);
                    stopSelf();
                    return START_NOT_STICKY;
                }
                worker = new Thread(this::processPackets, "AntiBet-DNS");
                worker.setDaemon(true);
                worker.start();
            } catch (Exception e) {
                running.set(false);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void processPackets() {
        byte[] packet = new byte[32767];
        try (FileInputStream in = new FileInputStream(tunnel.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tunnel.getFileDescriptor())) {
            while (running.get()) {
                int length = in.read(packet);
                if (length <= 0) continue;
                byte[] response = handleIpv4Packet(packet, length);
                if (response != null) out.write(response);
            }
        } catch (IOException e) {
            if (running.get()) stopSelf();
        } finally {
            running.set(false);
        }
    }

    private byte[] handleIpv4Packet(byte[] p, int length) {
        if (length < 28 || (p[0] >> 4 & 0x0F) != 4) return null;
        int ihl = (p[0] & 0x0F) * 4;
        if (ihl < 20 || length < ihl + 8) return null;
        if ((p[9] & 0xFF) != 17) return null; // UDP only
        int srcPort = u16(p, ihl);
        int dstPort = u16(p, ihl + 2);
        int udpLength = u16(p, ihl + 4);
        if (dstPort != DNS_PORT || udpLength < 8 || ihl + udpLength > length) return null;
        byte[] dns = Arrays.copyOfRange(p, ihl + 8, ihl + udpLength);
        if (dns.length < 12) return null;

        String domain = readQueryName(dns);
        if (domain == null) return null;
        byte[] dnsResponse;
        if (isBlocked(domain)) {
            dnsResponse = makeNxDomain(dns);
        } else {
            dnsResponse = forwardDns(dns);
            if (dnsResponse == null) dnsResponse = makeServFail(dns);
        }
        return buildIpv4UdpResponse(p, ihl, srcPort, dstPort, dnsResponse);
    }

    private byte[] forwardDns(byte[] query) {
        try (DatagramSocket socket = new DatagramSocket()) {
            if (!protect(socket)) return null;
            socket.connect(new InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS), DNS_PORT));
            socket.setSoTimeout(2500);
            socket.send(new DatagramPacket(query, query.length));
            byte[] buf = new byte[4096];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            return Arrays.copyOf(response.getData(), response.getLength());
        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String readQueryName(byte[] dns) {
        int qdCount = u16(dns, 4);
        if (qdCount < 1) return null;
        int pos = 12;
        StringBuilder name = new StringBuilder();
        while (pos < dns.length) {
            int size = dns[pos++] & 0xFF;
            if (size == 0) break;
            if ((size & 0xC0) != 0 || size > 63 || pos + size > dns.length) return null;
            if (name.length() > 0) name.append('.');
            name.append(new String(dns, pos, size, StandardCharsets.US_ASCII));
            pos += size;
        }
        if (pos + 4 > dns.length || name.length() == 0) return null;
        return name.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlocked(String domain) {
        for (String blocked : BLOCKED_DOMAINS) {
            if (domain.equals(blocked) || domain.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private static byte[] makeNxDomain(byte[] query) {
        byte[] response = Arrays.copyOf(query, 12 + questionLength(query));
        response[2] = (byte) 0x81; // response, recursion desired
        response[3] = (byte) 0x83; // recursion available + NXDOMAIN
        response[6] = response[7] = 0;
        response[8] = response[9] = 0;
        response[10] = response[11] = 0;
        return response;
    }

    private static byte[] makeServFail(byte[] query) {
        byte[] response = Arrays.copyOf(query, 12 + questionLength(query));
        response[2] = (byte) 0x81;
        response[3] = (byte) 0x82; // SERVFAIL
        response[6] = response[7] = 0;
        response[8] = response[9] = 0;
        response[10] = response[11] = 0;
        return response;
    }

    private static int questionLength(byte[] dns) {
        int pos = 12;
        while (pos < dns.length) {
            int size = dns[pos++] & 0xFF;
            if (size == 0) break;
            if ((size & 0xC0) != 0 || size > 63 || pos + size > dns.length) return 0;
            pos += size;
        }
        if (pos + 4 > dns.length) return 0;
        return pos + 4 - 12;
    }

    private static byte[] buildIpv4UdpResponse(byte[] request, int ihl,
                                                int clientPort, int serverPort, byte[] dns) {
        int udpLen = 8 + dns.length;
        int totalLen = 20 + udpLen;
        byte[] out = new byte[totalLen];
        out[0] = 0x45;
        out[1] = 0;
        put16(out, 2, totalLen);
        put16(out, 4, 0);
        put16(out, 6, 0x4000);
        out[8] = 64;
        out[9] = 17;
        // Reverse source/destination IPv4 addresses from the original packet.
        System.arraycopy(request, 16, out, 12, 4);
        System.arraycopy(request, 12, out, 16, 4);
        put16(out, 10, checksum(out, 0, 20));
        put16(out, 20, serverPort);
        put16(out, 22, clientPort);
        put16(out, 24, udpLen);
        put16(out, 26, 0);
        System.arraycopy(dns, 0, out, 28, dns.length);
        return out;
    }

    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int value = (data[offset + i] & 0xFF) << 8;
            if (i + 1 < length) value |= data[offset + i + 1] & 0xFF;
            sum += value;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum) & 0xFFFF;
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static void put16(byte[] b, int i, int value) {
        b[i] = (byte) (value >> 8);
        b[i + 1] = (byte) value;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Proteção AntiBet", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("AntiBet")
                    .setContentText("Filtro DNS local ativo (versão experimental)")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("AntiBet")
                .setContentText("Filtro DNS local ativo (versão experimental)")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (tunnel != null) {
            try { tunnel.close(); } catch (IOException ignored) { }
            tunnel = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
