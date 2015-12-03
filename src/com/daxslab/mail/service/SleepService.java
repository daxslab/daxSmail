package com.daxslab.mail.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.daxslab.mail.K9;
import com.daxslab.mail.helper.power.TracingPowerManager.TracingWakeLock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SleepService extends CoreService {

    private static String ALARM_FIRED = "com.daxslab.mail.service.SleepService.ALARM_FIRED";
    private static String LATCH_ID = "com.daxslab.mail.service.SleepService.LATCH_ID_EXTRA";


    private static ConcurrentHashMap<Integer, SleepDatum> sleepData = new ConcurrentHashMap<Integer, SleepDatum>();

    private static AtomicInteger latchId = new AtomicInteger();

    public static void sleep(Context context, long sleepTime, TracingWakeLock wakeLock, long wakeLockTimeout) {
        Integer id = latchId.getAndIncrement();
        if (K9.DEBUG)
            Log.d(K9.LOG_TAG, "SleepService Preparing CountDownLatch with id = " + id + ", thread " + Thread.currentThread().getName());
        SleepDatum sleepDatum = new SleepDatum();
        CountDownLatch latch = new CountDownLatch(1);
        sleepDatum.latch = latch;
        sleepDatum.reacquireLatch = new CountDownLatch(1);
        sleepData.put(id, sleepDatum);

        Intent i = new Intent();
        i.setClassName(context.getPackageName(), "com.daxslab.mail.service.SleepService");
        i.putExtra(LATCH_ID, id);
        i.setAction(ALARM_FIRED + "." + id);
        long startTime = System.currentTimeMillis();
        long nextTime = startTime + sleepTime;
        BootReceiver.scheduleIntent(context, nextTime, i);
        if (wakeLock != null) {
            sleepDatum.wakeLock = wakeLock;
            sleepDatum.timeout = wakeLockTimeout;
            wakeLock.release();
        }
        try {
            boolean countedDown = latch.await(sleepTime, TimeUnit.MILLISECONDS);
            if (!countedDown) {
                if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "SleepService latch timed out for id = " + id + ", thread " + Thread.currentThread().getName());
            }
        } catch (InterruptedException ie) {
            Log.e(K9.LOG_TAG, "SleepService Interrupted while awaiting latch", ie);
        }
        SleepDatum releaseDatum = sleepData.remove(id);
        if (releaseDatum == null) {
            try {
                if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "SleepService waiting for reacquireLatch for id = " + id + ", thread " + Thread.currentThread().getName());
                if (!sleepDatum.reacquireLatch.await(5000, TimeUnit.MILLISECONDS)) {
                    Log.w(K9.LOG_TAG, "SleepService reacquireLatch timed out for id = " + id + ", thread " + Thread.currentThread().getName());
                } else if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "SleepService reacquireLatch finished for id = " + id + ", thread " + Thread.currentThread().getName());
            } catch (InterruptedException ie) {
                Log.e(K9.LOG_TAG, "SleepService Interrupted while awaiting reacquireLatch", ie);
            }
        } else {
            reacquireWakeLock(releaseDatum);
        }

        long endTime = System.currentTimeMillis();
        long actualSleep = endTime - startTime;

        if (actualSleep < sleepTime) {
            Log.w(K9.LOG_TAG, "SleepService sleep time too short: requested was " + sleepTime + ", actual was " + actualSleep);
        } else {
            if (K9.DEBUG)
                Log.d(K9.LOG_TAG, "SleepService requested sleep time was " + sleepTime + ", actual was " + actualSleep);
        }
    }

    private static void endSleep(Integer id) {
        if (id != -1) {
            SleepDatum sleepDatum = sleepData.remove(id);
            if (sleepDatum != null) {
                CountDownLatch latch = sleepDatum.latch;
                if (latch == null) {
                    Log.e(K9.LOG_TAG, "SleepService No CountDownLatch available with id = " + id);
                } else {
                    if (K9.DEBUG)
                        Log.d(K9.LOG_TAG, "SleepService Counting down CountDownLatch with id = " + id);
                    latch.countDown();
                }
                reacquireWakeLock(sleepDatum);
                sleepDatum.reacquireLatch.countDown();
            } else {
                if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "SleepService Sleep for id " + id + " already finished");
            }
        }
    }

    private static void reacquireWakeLock(SleepDatum sleepDatum) {
        TracingWakeLock wakeLock = sleepDatum.wakeLock;
        if (wakeLock != null) {
            synchronized (wakeLock) {
                long timeout = sleepDatum.timeout;
                if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "SleepService Acquiring wakeLock for " + timeout + "ms");
                wakeLock.acquire(timeout);
            }
        }
    }

    @Override
    public int startService(Intent intent, int startId) {
        try {
          if (intent.getAction().startsWith(ALARM_FIRED)) {
              Integer id = intent.getIntExtra(LATCH_ID, -1);
              endSleep(id);
          }
          return START_NOT_STICKY;
        }
        finally {
          stopSelf(startId);
        }
    }

    private static class SleepDatum {
        CountDownLatch latch;
        TracingWakeLock wakeLock;
        long timeout;
        CountDownLatch reacquireLatch;
    }

}
