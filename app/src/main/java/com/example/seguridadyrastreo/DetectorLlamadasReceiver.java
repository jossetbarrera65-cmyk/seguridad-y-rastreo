package com.example.seguridadyrastreo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DetectorLlamadasReceiver extends BroadcastReceiver {

    public static final String ACTION_START = "com.example.seguridadyrastreo.START_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Aquí vivirá la detección de llamadas (RINGING, OFFHOOK)
        // y el disparador inteligente del servicio con la compensación de 10 segundos.
    }
}