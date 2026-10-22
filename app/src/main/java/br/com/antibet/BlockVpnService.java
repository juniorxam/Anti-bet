package br.com.antibet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * First-stage VPN service scaffold. It establishes a DNS-only tunnel and
 * keeps the lifecycle/foreground notification in place. DNS packet filtering
 * and forwarding must be completed and tested before treating this prototype
 * as an effective blocker.
 */
public class BlockVpnService extends VpnService {
    private static final String CHANNEL_ID = "antibet_protection";
    private ParcelFileDescriptor tunnel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(1, buildNotification());
        if (running.compareAndSet(false, true)) {
            try {
                tunnel = new Builder()
                        .setSession("AntiBet DNS")
                        .addAddress("10.77.0.1", 32)
                        .addDnsServer("10.77.0.2")
                        .addRoute("10.77.0.2", 32)
                        .setMtu(1500)
                        .establish();
                // Intentionally no claim of active filtering yet: packet parsing,
                // DNS forwarding, and checksum-safe responses are still pending.
                if (tunnel == null) {
                    running.set(false);
                    stopSelf();
                }
            } catch (Exception e) {
                running.set(false);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
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
                    .setContentText("Serviço de proteção experimental em execução")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("AntiBet")
                .setContentText("Serviço de proteção experimental em execução")
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
