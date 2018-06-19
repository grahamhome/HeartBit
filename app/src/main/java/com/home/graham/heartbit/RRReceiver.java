package com.home.graham.heartbit;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RRReceiver extends Thread {

    public static Handler rrHandler;

    private static final int DETECTION_WINDOW_SIZE = 45;
    private static final int CORRECTION_WINDOW_SIZE = 10;

    public static final int NEW_VALUE = 4;
    public static final int RECORDING_STARTED = 6;
    public static final int RECORDING_STOPPED = 7;
    public static final int USER_MESSAGE = 8;

    private static ArrayList<Float> timeSequentialRRValues = new ArrayList<>();
    private static ArrayList<Float> dRRValues = new ArrayList<>();
    private static ArrayList<Float> cleanedRRValues;

    private static boolean recording = false;

    private static Timer timer = new Timer();

    private static final boolean TESTING = true;

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
                            float rr = ((Polar.PolarTask) inputMessage.obj).rr;
                            if (!timeSequentialRRValues.isEmpty()) {
                                dRRValues.add(Math.abs(timeSequentialRRValues.get(timeSequentialRRValues.size()-1)-rr));
                            }
                            timeSequentialRRValues.add(rr);
                        }
                        break;
                    case BreathingCoach.TOGGLE_RECORDING:
                        recording = !recording;
                        timer.cancel();
                        timer.purge();
                        if (recording) {
                            timeSequentialRRValues.clear();
                            dRRValues.clear();
                            (timer = new Timer()).schedule(new DisconnectorDetector(), 10000);
                            BreathingCoach.uiMessageHandler.obtainMessage(RECORDING_STARTED).sendToTarget();
                        } else {
                            BreathingCoach.uiMessageHandler.obtainMessage(RECORDING_STOPPED).sendToTarget();
                            if (checkWriteCapability()) {
                                if (!TESTING) {
                                    writeData(false);
                                    cleanData();
                                    writeData(true);
                                } else {
                                    importData();
                                    cleanData();
                                    writeData(true);
                                }
                            }
                        }
                        break;
                }
            }
        };
    }

    // TODO: remove
    private static void importData() {
        timeSequentialRRValues.clear();
        dRRValues.clear();
        File root = android.os.Environment.getExternalStorageDirectory();
        File directory = new File(root.getAbsolutePath() + "/heartbit");
        File input = new File(directory, "5-18-2018-20-56-raw.txt");
        try {
            FileReader inputReader = new FileReader(input);
            BufferedReader bufferedReader = new BufferedReader(inputReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (!timeSequentialRRValues.isEmpty()) {
                    dRRValues.add(Math.abs(timeSequentialRRValues.get(timeSequentialRRValues.size()-1)-Float.parseFloat(line)));
                }
                timeSequentialRRValues.add(Float.parseFloat(line));
            }
            bufferedReader.close();
            inputReader.close();
        } catch (IOException x) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Error: failed to read from external storage").sendToTarget();
        }
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
        int size = dRRValues.size();
        cleanedRRValues = new ArrayList<>(timeSequentialRRValues);
        if (size < DETECTION_WINDOW_SIZE+1 || size < CORRECTION_WINDOW_SIZE+1) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Data is too small to process for correction").sendToTarget();
            return;
        }
        for (int i=0; i<size; i++) {
            float drr = dRRValues.get(i);
            boolean clean = false;
            // All values must be in the range of a normal heart rate.
            if (!isValid(i)) {
                ArrayList<Float> window = new ArrayList<>();
                boolean searching = true;
                int offset = 1;
                while (window.size() < CORRECTION_WINDOW_SIZE && searching) {
                    if (i-offset < 0 && i+offset > cleanedRRValues.size()-1) {
                        searching = false;
                        break;
                    }
                    if (i-offset > 0 && isValid(i-offset)) {
                        window.add(cleanedRRValues.get(i-offset));
                    }
                    if (i+offset < cleanedRRValues.size()-1 && isValid(i+offset)) {
                        window.add(cleanedRRValues.get(i+offset));
                    }
                    offset++;
                }
                cleanedRRValues.remove(i);
                if (i < cleanedRRValues.size()-2) {
                    cleanedRRValues.add(i, mean(window));
                } else {
                    cleanedRRValues.add(mean(window));
                }
            }
        }
    }

    private static boolean isValid(int index) {
        int size = dRRValues.size();
        float drr = dRRValues.get(index);
        List<Float> window;
        if (index < DETECTION_WINDOW_SIZE/2) {
            window = new ArrayList<>(dRRValues.subList(0, DETECTION_WINDOW_SIZE+1));
            window.remove(drr);
        } else if (index+(DETECTION_WINDOW_SIZE/2)+1 > size) {
            window = new ArrayList<>(dRRValues.subList(size-DETECTION_WINDOW_SIZE-1, size));
            window.remove(drr);
        } else {
            window = new ArrayList<>(dRRValues.subList(index-(DETECTION_WINDOW_SIZE/2), index+(DETECTION_WINDOW_SIZE/2)+1));
            window.remove(drr);
        }
        return drr <= (quartileDeviation(window) * 5.2);
    }

    private static float quartileDeviation(List<Float> data) {
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
        size = size - (size%4);
        return (((orderedData.get((size/4)-1) + orderedData.get((size/4)+1))/2) +
                ((orderedData.get(((size*3)/4)-1) + orderedData.get(((size*3)/4)+1))/2))/2;
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
