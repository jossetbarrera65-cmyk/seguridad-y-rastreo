package com.example.seguridadyrastreo;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

public class RastreadorGpsService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "rastreo_channel";
    private static final int INTERVALO_MS = 300000; // 5 minutos en milisegundos

    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient locClient;

    @Override
    public void onCreate() {
        super.onCreate();
        // 1. Preparamos el cliente de GPS de Google
        locClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 2. Android 12 exige que el servicio se declare en primer plano en los primeros 5 segundos
        crearNotificacionFija();

        // 3. Adquirimos un WakeLock para evitar que el CPU se duerma
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SeguridadApp::GpsWakeLock");
            wakeLock.acquire(30000); // Lo soltamos máximo a los 30 seg
        }

        // 4. Obtenemos la ubicación y enviamos el SMS
        obtenerUbicacionYEnviar();

        // 5. Programamos la repetición exacta para dentro de 5 minutos
        programarSiguienteEjecucion();

        return START_STICKY; // Si el sistema lo mata por memoria, que lo vuelva a encender
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacionYEnviar() {
        // Pedimos la ubicación con alta precisión
        locClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            enviarSms(location);
                        } else {
                            Log.e("Rastreador", "No se pudo obtener la ubicación");
                            // Si falla, liberamos el WakeLock para no drenar batería
                            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                        }
                    }
                });
    }

    private void enviarSms(Location loc) {
        SharedPreferences prefs = getSharedPreferences("PreferenciasSeguridad", Context.MODE_PRIVATE);
        String numeroDestino = prefs.getString("numero_emergencia", "");

        if (numeroDestino.isEmpty()) return;

        // Armamos el enlace de Google Maps
        String link = "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        String mensaje = "ALERTA DE SEGURIDAD. Ubicación actual: " + link;

        try {
            SmsManager smsManager = SmsManager.getDefault();
            // Dividimos el mensaje por si supera el límite de caracteres de un SMS normal
            ArrayList<String> partes = smsManager.divideMessage(mensaje);
            smsManager.sendMultipartTextMessage(numeroDestino, null, partes, null, null);
            Log.d("Rastreador", "SMS enviado exitosamente a: " + numeroDestino);
        } catch (Exception e) {
            Log.e("Rastreador", "Error al enviar SMS: " + e.getMessage());
        } finally {
            // Ya enviamos el SMS, podemos dejar que el CPU descanse
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void programarSiguienteEjecucion() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DetectorLlamadasReceiver.class);
        intent.setAction(DetectorLlamadasReceiver.ACTION_START);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 777, intent, flags);

        if (am != null) {
            long trigger = SystemClock.elapsedRealtime() + INTERVALO_MS;
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        }
    }

    private void crearNotificacionFija() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Servicio de Rastreo Activo",
                    NotificationManager.IMPORTANCE_LOW // Low para que no haga ruido, solo se vea el ícono
            );
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notificacion = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Escudo de Seguridad")
                .setContentText("Rastreo de emergencia en ejecución")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notificacion);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}