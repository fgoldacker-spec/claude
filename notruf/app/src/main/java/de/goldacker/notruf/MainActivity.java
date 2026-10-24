package de.goldacker.notruf;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emergency screen: holding the red button sends an SMS with the current location to all
 * contacts at once and then calls them one after another until the user presses "Stopp".
 */
public class MainActivity extends Activity {
    private static final String[] PERMISSIONS = {
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    };
    private static final long LOCATION_TIMEOUT_MS = 10_000;
    private static final long FRESH_LOCATION_MS = 2 * 60_000;
    private static final int NEXT_CALL_DELAY_S = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private PanicButton panic;
    private TextView status;
    private Button stop;

    // Alarm sequence state.
    private boolean active;
    private List<Prefs.Contact> contacts = new ArrayList<>();
    private int callIndex;
    private boolean callRunning;   // ACTION_CALL started, end not yet handled
    private boolean callEnded;     // phone went back to idle after this call
    private boolean sawOffhook;
    private long callStartedAt;
    private boolean resumed;
    private int countdown;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean locationDone;
    private Location bestLocation;

    private TelephonyManager telephony;
    private PhoneStateListener phoneListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        requestMissingPermissions();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView gear = new TextView(this);
        gear.setText("\u2699");
        gear.setTextColor(0xFFBBBBBB);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        gear.setPadding(dp(12), 0, dp(12), dp(4));
        gear.setContentDescription("Einstellungen");
        gear.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gearLp.gravity = Gravity.END;
        root.addView(gear, gearLp);

        panic = new PanicButton(this);
        panic.setOnTriggerListener(this::startAlarm);
        root.addView(panic, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        stop = new Button(this);
        stop.setText("STOPP – Hilfe erreicht");
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        stop.setMinHeight(dp(64));
        stop.setVisibility(View.GONE);
        stop.setOnClickListener(v -> finishAlarm("Abgebrochen. Kontakte wurden per SMS informiert."));
        root.addView(stop, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (!active) {
            int n = Prefs.contacts(this).size();
            status.setText(n == 0
                ? "Noch keine Notfallnummern – bitte über \u2699 eintragen."
                : n + (n == 1 ? " Notfallnummer" : " Notfallnummern") + " hinterlegt.");
            return;
        }
        // Without phone-state access, coming back to the app is the only sign that a call ended.
        if (callRunning && phoneListener == null && SystemClock.elapsedRealtime() - callStartedAt > 3000) {
            callEnded = true;
        }
        if (callRunning && callEnded) onCallEnded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    protected void onDestroy() {
        stopLocation();
        stopPhoneListener();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void openSettings() {
        if (active) {
            Toast.makeText(this, "Bitte zuerst den laufenden Alarm stoppen.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ---- Alarm flow: location -> SMS to all -> calls one by one ----

    private void startAlarm() {
        if (active) return;
        contacts = Prefs.contacts(this);
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Bitte zuerst Notfallnummern eintragen.", Toast.LENGTH_LONG).show();
            openSettings();
            return;
        }
        active = true;
        vibrate();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        panic.setEnabled(false);
        panic.setHint("Alarm läuft");
        stop.setVisibility(View.VISIBLE);
        status.setText("Standort wird ermittelt …");
        findLocation();
    }

    private void findLocation() {
        locationDone = false;
        bestLocation = null;
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !has(Manifest.permission.ACCESS_FINE_LOCATION)
                && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            onLocationReady(null);
            return;
        }
        for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                consider(locationManager.getLastKnownLocation(p));
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        if (bestLocation != null && System.currentTimeMillis() - bestLocation.getTime() < FRESH_LOCATION_MS
                && bestLocation.getAccuracy() < 100) {
            onLocationReady(bestLocation);
            return;
        }
        // All four methods are implemented: before API 30 they have no default implementation.
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                consider(location);
                if (location.getAccuracy() < 50) onLocationReady(bestLocation);
            }

            @Override
            public void onStatusChanged(String provider, int s, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
        boolean requested = false;
        for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (locationManager.isProviderEnabled(p)) {
                    locationManager.requestLocationUpdates(p, 0, 0, locationListener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        if (!requested) {
            onLocationReady(bestLocation);
            return;
        }
        handler.postDelayed(() -> onLocationReady(bestLocation), LOCATION_TIMEOUT_MS);
    }

    private void consider(Location l) {
        if (l == null) return;
        if (bestLocation == null || l.getTime() > bestLocation.getTime() + 60_000
                || l.getAccuracy() < bestLocation.getAccuracy()) {
            bestLocation = l;
        }
    }

    private void onLocationReady(Location location) {
        if (locationDone || !active) return;
        locationDone = true;
        stopLocation();
        int sent = sendSms(location);
        String smsInfo = has(Manifest.permission.SEND_SMS)
            ? "SMS an " + sent + " von " + contacts.size() + " Kontakten gesendet."
            : "SMS nicht erlaubt – keine SMS gesendet.";
        Toast.makeText(this, smsInfo, Toast.LENGTH_LONG).show();
        startPhoneListener();
        callIndex = 0;
        callCurrent();
    }

    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {
            }
        }
        locationListener = null;
    }

    private int sendSms(Location location) {
        if (!has(Manifest.permission.SEND_SMS)) return 0;
        SmsManager sms;
        try {
            sms = Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class) : SmsManager.getDefault();
        } catch (RuntimeException e) {
            return 0;
        }
        if (sms == null) return 0;
        ArrayList<String> parts = sms.divideMessage(buildMessage(location));
        int sent = 0;
        for (Prefs.Contact c : contacts) {
            try {
                sms.sendMultipartTextMessage(c.number, null, parts, null, null);
                sent++;
            } catch (RuntimeException ignored) {
            }
        }
        return sent;
    }

    private String buildMessage(Location l) {
        StringBuilder sb = new StringBuilder(Prefs.message(this));
        String name = Prefs.name(this);
        if (!name.isEmpty()) sb.append("\n– ").append(name);
        if (l != null) {
            sb.append(String.format(Locale.US, "\nStandort: https://maps.google.com/?q=%.6f,%.6f (±%d m)",
                l.getLatitude(), l.getLongitude(), Math.round(l.getAccuracy())));
        } else {
            sb.append("\nStandort: unbekannt");
        }
        return sb.toString();
    }

    private void callCurrent() {
        if (!active) return;
        if (callIndex >= contacts.size()) {
            finishAlarm("Alle Notfallnummern wurden angerufen.");
            return;
        }
        Prefs.Contact c = contacts.get(callIndex);
        status.setText("Rufe an (" + (callIndex + 1) + "/" + contacts.size() + "):\n" + c.label());
        Uri uri = Uri.fromParts("tel", c.number, null);
        if (!has(Manifest.permission.CALL_PHONE)) {
            // Without the permission Android only allows opening the dialer with the number.
            startActivity(new Intent(Intent.ACTION_DIAL, uri));
            finishAlarm("Anrufen nicht erlaubt – Nummer im Telefon geöffnet.");
            return;
        }
        callRunning = true;
        callEnded = false;
        sawOffhook = false;
        callStartedAt = SystemClock.elapsedRealtime();
        try {
            startActivity(new Intent(Intent.ACTION_CALL, uri));
        } catch (RuntimeException e) {
            callRunning = false;
            Toast.makeText(this, "Anruf fehlgeschlagen: " + c.label(), Toast.LENGTH_SHORT).show();
            callIndex++;
            handler.postDelayed(this::callCurrent, 1000);
        }
    }

    private void onCallEnded() {
        callRunning = false;
        callEnded = false;
        callIndex++;
        if (callIndex >= contacts.size()) {
            finishAlarm("Alle Notfallnummern wurden angerufen.");
            return;
        }
        countdown = NEXT_CALL_DELAY_S;
        tick();
    }

    private void tick() {
        if (!active) return;
        if (countdown <= 0) {
            callCurrent();
            return;
        }
        status.setText("Nächster Anruf in " + countdown + " s:\n" + contacts.get(callIndex).label()
            + "\n\nJemand erreicht? Dann STOPP drücken.");
        countdown--;
        handler.postDelayed(this::tick, 1000);
    }

    private void finishAlarm(String message) {
        active = false;
        callRunning = false;
        handler.removeCallbacksAndMessages(null);
        stopLocation();
        stopPhoneListener();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        panic.setEnabled(true);
        panic.setHint("2 Sekunden gedrückt halten");
        stop.setVisibility(View.GONE);
        status.setText(message);
    }

    // ---- Call state ----

    @SuppressWarnings("deprecation")
    private void startPhoneListener() {
        if (phoneListener != null || !has(Manifest.permission.READ_PHONE_STATE)) return;
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony == null) return;
        phoneListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    sawOffhook = true;
                } else if (state == TelephonyManager.CALL_STATE_IDLE && callRunning && sawOffhook) {
                    callEnded = true;
                    // Starting the next call from the background is blocked, so wait for onResume.
                    if (resumed) onCallEnded();
                }
            }
        };
        try {
            telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (SecurityException e) {
            phoneListener = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void stopPhoneListener() {
        if (telephony != null && phoneListener != null) {
            telephony.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        }
        phoneListener = null;
    }

    // ---- Helpers ----

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(500);
        }
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : PERMISSIONS) if (!has(p)) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 1);
    }

    private boolean has(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
