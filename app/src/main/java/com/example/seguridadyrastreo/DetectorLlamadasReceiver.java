package com.example.seguridadyrastreo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

public class DetectorLlamadasReceiver extends BroadcastReceiver {

    public static final String ACTION_START = "com.example.seguridadyrastreo.START_SERVICE";
    private static final int REQ_ACTIVATION = 777;
    private static final int DELAY_MS = 6000; // 6 seg de espera + 4 de antena GPS = 10 seg exactos

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Abrimos la bóveda de memoria que creamos en el MainActivity
        SharedPreferences prefs = context.getSharedPreferences("PreferenciasSeguridad", Context.MODE_PRIVATE);
        String numeroEmergencia = prefs.getString("numero_emergencia", "");
        String action = intent.getAction();

        // Si no hay número guardado o no hay acción, lo ignoramos
        if (numeroEmergencia.isEmpty() || action == null) return;

        // 2. EL DISPARADOR (Se activa cuando la alarma de 6 segundos termina)
        if (ACTION_START.equals(action)) {
            Intent serviceIntent = new Intent(context, RastreadorGpsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            return;
        }

        // 3. DETECCIÓN DE LLAMADAS (Entrantes y Salientes)
        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            // A. ESTADO: SONANDO (Llamada Entrante)
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                if (incomingNumber == null) {
                    // En Android 12 el número puede venir nulo por privacidad, lo buscamos en el historial
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        String lateNumber = obtenerNumeroDelHistorial(context, CallLog.Calls.INCOMING_TYPE);
                        verificarYProgramar(context, numeroEmergencia, lateNumber, prefs);
                    }, 2000);
                } else {
                    verificarYProgramar(context, numeroEmergencia, incomingNumber, prefs);
                }
            }
            // B. ESTADO: DESCOLGADO (Se contestó la llamada o marcaste tú)
            else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                String ultimoSonando = prefs.getString("ultimo_numero_sonando", "");

                if (!ultimoSonando.isEmpty() && PhoneNumberUtils.compare(context, numeroEmergencia, ultimoSonando)) {
                    // Contestaste la llamada a tiempo. Apagamos todo.
                    detenerRastreo(context);
                } else {
                    // Es una llamada saliente. Revisamos si le estás regresando la llamada a tu contacto.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        String lastOutgoing = obtenerNumeroDelHistorial(context, CallLog.Calls.OUTGOING_TYPE);
                        if (lastOutgoing != null && PhoneNumberUtils.compare(context, numeroEmergencia, lastOutgoing)) {
                            detenerRastreo(context);
                        }
                    }, 2000);
                    cancelarAlarma(context); // Cancelamos por si apenas estaba contando los 10 segundos
                }
                prefs.edit().remove("ultimo_numero_sonando").apply();
            }
            // C. ESTADO: COLGADO (Fin de la llamada)
            else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                cancelarAlarma(context);
                prefs.edit().remove("ultimo_numero_sonando").apply();
            }
        }
    }

    // --- MÉTODOS DE APOYO ---

    private void verificarYProgramar(Context context, String numeroGuardado, String numeroEntrante, SharedPreferences prefs) {
        if (numeroEntrante != null && PhoneNumberUtils.compare(context, numeroGuardado, numeroEntrante)) {
            prefs.edit().putString("ultimo_numero_sonando", numeroEntrante).apply();

            // Programamos la alarma de Android para detonar en 6 segundos exactos
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, DetectorLlamadasReceiver.class).setAction(ACTION_START);

            // FLAG_IMMUTABLE es el candado obligatorio de seguridad en Android 12+
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(context, REQ_ACTIVATION, intent, flags);

            if (am != null) {
                long trigger = SystemClock.elapsedRealtime() + DELAY_MS;
                if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                }
            }
        }
    }

    private void detenerRastreo(Context context) {
        // Matamos el servicio de SMS y cancelamos el cronómetro
        context.stopService(new Intent(context, RastreadorGpsService.class));
        cancelarAlarma(context);
    }

    private void cancelarAlarma(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, DetectorLlamadasReceiver.class).setAction(ACTION_START);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(context, REQ_ACTIVATION, intent, flags);
        if (am != null) am.cancel(pi);
    }

    private String obtenerNumeroDelHistorial(Context context, int tipoLlamada) {
        String number = null;
        try {
            Uri calls = CallLog.Calls.CONTENT_URI;
            Cursor cursor = context.getContentResolver().query(calls, null,
                    CallLog.Calls.TYPE + " = " + tipoLlamada,
                    null, CallLog.Calls.DATE + " DESC LIMIT 1");
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    number = cursor.getString(numIdx);
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return number;
    }
}