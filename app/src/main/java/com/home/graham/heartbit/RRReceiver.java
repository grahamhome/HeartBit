package com.home.graham.heartbit;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RRReceiver extends Thread {

    public static Handler rrHandler;

    private static final int DETECTION_WINDOW_SIZE = 20;
    private static final int CORRECTION_WINDOW_SIZE = 2;

    public static final int NEW_VALUE = 4;
    public static final int RECORDING_STARTED = 6;
    public static final int RECORDING_STOPPED = 7;
    public static final int USER_MESSAGE = 8;

    private static ArrayList<Float> timeSequentialRRValues = new ArrayList<>();
    private static ArrayList<Float> cleanedRRValues;

    private static boolean recording = false;

    private static Timer timer = new Timer();

    @Override
    public void run() {
        setUpMessageHandler();
    }

    private static void setUpMessageHandler() {
        rrHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case Polar.CONNECTED:
                        if (recording) {
                            (timer = new Timer()).schedule(new DisconnectorDetector(), 10000);
                        }
                        BreathingCoach.uiMessageHandler.obtainMessage(Polar.CONNECTED).sendToTarget();
                        break;
                    case Polar.NEW_MEASUREMENT:
                        if (recording) {
                            timer.cancel();
                            timer.purge();
                            (timer = new Timer()).schedule(new DisconnectorDetector(), 10000);
                            BreathingCoach.uiMessageHandler.obtainMessage(Polar.NEW_MEASUREMENT).sendToTarget();
                            timeSequentialRRValues.add(((Polar.PolarTask) inputMessage.obj).rr);
                        }
                        break;
                    case BreathingCoach.TOGGLE_RECORDING:
                        recording = !recording;
                        timer.cancel();
                        timer.purge();
                        if (recording) {
                            timeSequentialRRValues.clear();
                            (timer = new Timer()).schedule(new DisconnectorDetector(), 10000);
                            BreathingCoach.uiMessageHandler.obtainMessage(RECORDING_STARTED).sendToTarget();
                        } else {
                            BreathingCoach.uiMessageHandler.obtainMessage(RECORDING_STOPPED).sendToTarget();
                            if (checkWriteCapability()) {
                                writeData(false);
                                cleanData();
                                writeData(true);
                            }
                        }
                        break;
                }
            }
        };
    }

    private static boolean checkWriteCapability() {
        // TODO: add permission check here
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Error: can't write to external storage").sendToTarget();
            return false;
        }
        return true;
    }

    private static void writeData(boolean cleaned) {
        ArrayList<Float> data = (cleaned ? cleanedRRValues : timeSequentialRRValues);
        if (data.isEmpty()) {
            return;
        }
        File root = android.os.Environment.getExternalStorageDirectory();
        File directory = new File(root.getAbsolutePath() + "/heartbit");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Calendar now = Calendar.getInstance();
        String name = now.get(Calendar.MONTH) + "-" + now.get(Calendar.DAY_OF_MONTH) + "-" + now.get(Calendar.YEAR) + "-" +
                now.get(Calendar.HOUR_OF_DAY) + "-" + now.get(Calendar.MINUTE) + "-" + (cleaned ? "cleaned" : "raw") + ".txt";
        File output = new File(directory, name);
        try {
            FileOutputStream outputStream = new FileOutputStream(output);
            PrintWriter outputWriter = new PrintWriter(outputStream);
            for (float rr : data) {
                outputWriter.println(Math.round(rr));
            }
            outputWriter.flush();
            outputWriter.close();
            outputStream.close();
        } catch (IOException x) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Error: failed to write to external storage").sendToTarget();
        }
        BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, (cleaned ? "Cleaned" : "Raw") + " RR values written to storage").sendToTarget();
    }

    private static void cleanData() {
        int size = timeSequentialRRValues.size();
        cleanedRRValues = new ArrayList<>(timeSequentialRRValues);
        if (size < DETECTION_WINDOW_SIZE+1 || size < CORRECTION_WINDOW_SIZE+1) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Data is too small to process for correction").sendToTarget();
            return;
        }
        for (int i=0; i<size; i++) {
            float rr = cleanedRRValues.get(i);
            boolean clean = false;
            // All values must be in the range of a normal heart rate.
            boolean normal = rr >= 500 && rr <= 2000;
            if (normal) {
                List<Float> window;
                if (i < DETECTION_WINDOW_SIZE/2) {
                    window = new ArrayList<>(cleanedRRValues.subList(0, DETECTION_WINDOW_SIZE+1));
                    window.remove(rr);
                } else if (i+(DETECTION_WINDOW_SIZE/2)+1 > size) {
                    window = new ArrayList<>(cleanedRRValues.subList(size-DETECTION_WINDOW_SIZE-1, size));
                    window.remove(rr);
                } else {
                    window = new ArrayList<>(cleanedRRValues.subList(i-(DETECTION_WINDOW_SIZE/2), i+(DETECTION_WINDOW_SIZE/2)+1));
                    window.remove(rr);
                }
                if (rr <= median(window) + 450) { // TODO: Adjust threshold based on BPM?
                    cleanedRRValues.remove(i);
                    cleanedRRValues.add(i,rr);
                    clean = true;
                }
            }
            if (!clean) {
                List<Float> window = new ArrayList<>();
                if (i < CORRECTION_WINDOW_SIZE/2) {
                    window = new ArrayList<>(cleanedRRValues.subList(0, CORRECTION_WINDOW_SIZE));
                    window.remove(rr);
                } else if (i+(CORRECTION_WINDOW_SIZE/2)+1 > size) {
                    window = new ArrayList<>(cleanedRRValues.subList(size-CORRECTION_WINDOW_SIZE, size));
                    window.remove(rr);
                } else {
                    window = new ArrayList<>(cleanedRRValues.subList(i-(CORRECTION_WINDOW_SIZE/2), i+(CORRECTION_WINDOW_SIZE/2)+1));
                    window.remove(rr);
                }
                cleanedRRValues.remove(i);
                cleanedRRValues.add(1, mean(window));
            }
        }
    }

    private static float median(List<Float> data) {
        int size = data.size();
        if (size == 0) {
            return 0;
        }
        ArrayList<Float> orderedData = new ArrayList<>(size);
        for (float f : data) {
            if (orderedData.isEmpty()) {
                orderedData.add(f);
            } else {
                int i = 0;
                while (orderedData.size() > i && orderedData.get(i) < f) {
                    i++;
                }
                if (i < orderedData.size() - 1) {
                    orderedData.add(i, f);
                } else {
                    orderedData.add(f);
                }
            }
        }
        if (size % 2 == 0) {
            return (orderedData.get((size/2)-1) + orderedData.get((size/2)+1))/2;
        } else {
            return orderedData.get((size-1)/2);
        }
    }

    private static float mean(List<Float> data) {
        if (data.isEmpty()) {
            return 0;
        }
        float sum = 0;
        for (float f : data) {
            sum += f;
        }
        return sum/data.size();
    }

    private static class DisconnectorDetector extends TimerTask {
        @Override
        public void run() {
            if (recording) {
                BreathingCoach.uiMessageHandler.obtainMessage(Polar.TIMEOUT).sendToTarget();
            }
        }
    };


}
