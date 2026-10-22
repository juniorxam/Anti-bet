package br.com.antibet;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 1001;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 48, 28, 28);
        root.setBackgroundColor(0xFFF7F8FA);

        TextView title = new TextView(this);
        title.setText("AntiBet");
        title.setTextSize(32);
        title.setTextColor(0xFF152238);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Mais controle sobre sites de apostas e cassinos");
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(0xFF475569);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = 12;
        root.addView(subtitle, sp);

        status = new TextView(this);
        status.setText("Proteção desligada");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(0xFFB42318);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = 36;
        stp.bottomMargin = 18;
        root.addView(status, stp);

        Button start = new Button(this);
        start.setText("Ativar proteção");
        start.setOnClickListener(v -> {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
            else onActivityResult(VPN_REQUEST, RESULT_OK, null);
        });
        root.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button stop = new Button(this);
        stop.setText("Desativar proteção");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, BlockVpnService.class));
            status.setText("Proteção desligada");
            status.setTextColor(0xFFB42318);
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 8;
        root.addView(stop, bp);

        TextView info = new TextView(this);
        info.setText("O AntiBet é experimental. Ele não consegue bloquear todos os anúncios, especialmente os exibidos dentro de vídeos e redes sociais. A VPN local é usada apenas para filtrar DNS.");
        info.setTextSize(14);
        info.setTextColor(0xFF475569);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = 32;
        root.addView(info, ip);

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startService(new Intent(this, BlockVpnService.class));
            status.setText("Proteção solicitada");
            status.setTextColor(0xFF067647);
        }
    }
}
