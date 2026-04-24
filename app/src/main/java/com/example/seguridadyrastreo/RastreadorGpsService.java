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
import android.hardware.camera2.CameraManager;
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
        locClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        crearNotificacionFija();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SeguridadApp::GpsWakeLock");
            wakeLock.acquire(30000);
        }

        // ¡AQUÍ ACTIVAMOS LA NUEVA BALIZA SOS!
        lanzarBalizaSOS();

        // Y paralelamente sacamos el GPS y mandamos el SMS
        obtenerUbicacionYEnviar();

        programarSiguienteEjecucion();

        return START_STICKY;
    }

    // --- NUEVA FUNCIONALIDAD: BALIZA SOS EN SEGUNDO PLANO ---
    private void lanzarBalizaSOS() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) return;

        // Creamos un hilo fantasma para que la app no se trabe mientras parpadea la luz
        new Thread(() -> {
            try {
                // Obtenemos el ID de la cámara trasera principal (generalmente es el "0")
                String cameraId = cameraManager.getCameraIdList()[0];

                // Repetimos el ciclo S-O-S exactamente 5 veces
                for (int ciclo = 0; ciclo < 5; ciclo++) {

                    // Letra S (Tres destellos cortos)
                    for (int i = 0; i < 3; i++) {
                        cameraManager.setTorchMode(cameraId, true);
                        Thread.sleep(150); // Encendido
                        cameraManager.setTorchMode(cameraId, false);
                        Thread.sleep(150); // Apagado
                    }
                    Thread.sleep(300); // Pausa entre letras

                    // Letra O (Tres destellos largos)
                    for (int i = 0; i < 3; i++) {
                        cameraManager.setTorchMode(cameraId, true);
                        Thread.sleep(500); // Encendido largo
                        cameraManager.setTorchMode(cameraId, false);
                        Thread.sleep(150); // Apagado
                    }
                    Thread.sleep(300); // Pausa entre letras

                    // Letra S (Tres destellos cortos)
                    for (int i = 0; i < 3; i++) {
                        cameraManager.setTorchMode(cameraId, true);
                        Thread.sleep(150);
                        cameraManager.setTorchMode(cameraId, false);
                        Thread.sleep(150);
                    }

                    // Pausa larga de 1.5 segundos antes del siguiente ciclo SOS
                    Thread.sleep(1500);
                }
            } catch (Exception e) {
                Log.e("Rastreador", "Error en la baliza SOS: " + e.getMessage());
            }
        }).start(); // ¡Arrancamos el hilo!
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacionYEnviar() {
        locClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            enviarSms(location);
                        } else {
                            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                        }
                    }
                });
    }

    private void enviarSms(Location loc) {
        SharedPreferences prefs = getSharedPreferences("PreferenciasSeguridad", Context.MODE_PRIVATE);
        String numeroDestino = prefs.getString("numero_emergencia", "");

        if (numeroDestino.isEmpty()) return;

        String link = "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        String mensaje = "ALERTA DE SEGURIDAD. Ubicación actual: " + link;

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> partes = smsManager.divideMessage(mensaje);
            smsManager.sendMultipartTextMessage(numeroDestino, null, partes, null, null);
        } catch (Exception e) {
            Log.e("Rastreador", "Error al enviar SMS: " + e.getMessage());
        } finally {
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
                    NotificationManager.IMPORTANCE_LOW
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