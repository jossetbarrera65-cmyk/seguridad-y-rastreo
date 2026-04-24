package com.example.seguridadyrastreo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

public class RastreadorGpsService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        // Aquí configuraremos el Servicio en Primer Plano y los WakeLocks
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Aquí estará la lógica pesada: obtener coordenadas de Google Services,
        // dividir el SMS, enviarlo y programar la siguiente repetición a los 5 mins.

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No usamos clientes enlazados
    }
}