package com.home.graham.heartbit;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedReader;
import java.io.File;
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

    private static final int DETECTION_WINDOW_SIZE = 20;
    private static final int CORRECTION_WINDOW_SIZE = 2;
    private static final int MEDIAN_THRESHOLD = 250;

    private static final int DRR_DETECTION_WINDOW_SIZE = 90;
    private static final double DRR_THRESHOLD = 3; //5.2;

    public static final int NEW_VALUE = 4;
    public static final int RECORDING_STARTED = 6;
    public static final int RECORDING_STOPPED = 7;
    public static final int USER_MESSAGE = 8;
    public static final int DEVICE_ID_FOUND = 9;

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
                                writeDataRemote(false);
                                cleanData();
                                writeDataRemote(true);
                            }
                        }
                        break;
                    case DEVICE_ID_FOUND:
                        UserData.setMonitorID(inputMessage.obj.toString(), BreathingCoach.currentActivity);
                        break;
                }
            }
        };
    }

    // TODO: remove
    private static void importData() {
        timeSequentialRRValues.clear();
        File root = android.os.Environment.getExternalStorageDirectory();
        File directory = new File(root.getAbsolutePath() + "/heartbit");
        File input = new File(directory, "2018-06-21 15-00-16.txt");
        try {
            FileReader inputReader = new FileReader(input);
            BufferedReader bufferedReader = new BufferedReader(inputReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
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

    private static void writeDataRemote(boolean cleaned) {
        ArrayList<Float> data = (cleaned ? cleanedRRValues : timeSequentialRRValues);
        if (data.isEmpty()) {
            return;
        }
        StringBuilder rrString = new StringBuilder();
        for (float rr : data) {
            if (rrString.length() > 0) {
                rrString.append("\r\n");
            }
            rrString.append(rr);
        }
        Calendar now = Calendar.getInstance();
        String fileName = now.get(Calendar.MONTH) + "-" + now.get(Calendar.DAY_OF_MONTH) + "-" + now.get(Calendar.YEAR) + "-" +
                now.get(Calendar.HOUR_OF_DAY) + "-" + now.get(Calendar.MINUTE) + "-" + (cleaned ? "cleaned" : "raw") + ".txt";
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageReference = storage.getReference().child("RR-Values/" + fileName);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        auth.signInAnonymously();
        UploadTask uploadTask = storageReference.putBytes(rrString.toString().getBytes());
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, (exception.getCause())).sendToTarget();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, ("File written to Firebase")).sendToTarget();
            }
        });
    }

    private static void cleanData() {
        int size = timeSequentialRRValues.size();
        cleanedRRValues = new ArrayList<>(timeSequentialRRValues);
        if (size < DETECTION_WINDOW_SIZE+1 || size < CORRECTION_WINDOW_SIZE+1 || size < DRR_DETECTION_WINDOW_SIZE) {
            BreathingCoach.uiMessageHandler.obtainMessage(USER_MESSAGE, "Data is too small to process for correction").sendToTarget();
            return;
        }
        for (int i=0; i<size; i++) {
            float rr = cleanedRRValues.get(i);
            boolean clean = false;
            // All values must be in the range of a normal heart rate.
            boolean normal = rr >= 100 && rr <= 2000;
            if (!normal || !isValid(i) || (i > 0 && !isValidDRR(i))) {
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

    private static boolean isValidDRR(int index) {
        // Get left and right-hand drrs
        float leftDRR = cleanedRRValues.get(index) - cleanedRRValues.get(index-1);
        //float rightDRR = cleanedRRValues.get(index+1) - cleanedRRValues.get(index);
        ArrayList<Float> window = new ArrayList<>(0);
        int offset = 2;
        boolean done = false;
        while (!done) {
            boolean newValue = false;
            if (index-offset >= 0) {
                window = insertInOrder(cleanedRRValues.get((index-offset)+1) - cleanedRRValues.get((index-offset)), window);
                newValue = true;
                if (window.size() == DRR_DETECTION_WINDOW_SIZE) {
                    done = true;
                    break;
                }
            }
            if (index+offset < cleanedRRValues.size()) {
                window = insertInOrder(cleanedRRValues.get(index+offset) - cleanedRRValues.get((index+offset)-1), window);
                newValue = true;
                if (window.size() == DRR_DETECTION_WINDOW_SIZE) {
                    done = true;
                    break;
                }
            }
            offset++;
            if (!newValue) {
                done = true;
            }
        }
        double adjustedQuartile = quartile(window) * DRR_THRESHOLD;
        if (Math.abs(leftDRR) <= adjustedQuartile) { //|| Math.abs(rightDRR) <= adjustedQuartile) {
            return true;
        } else {
            return false;
        }
        //return (Math.abs(leftDRR) <= adjustedQuartile || Math.abs(rightDRR) <= adjustedQuartile);
    }

    private static float quartile(List<Float> data) {
        ArrayList<Float> orderedData = order(data);
        int size = orderedData.size();
        int q1Index = (int) Math.floor(size * 0.25);
        int q3Index = (int) Math.floor(size * 0.75);
        float q1, q3 = 0;
        if (size%4 == 0) {
            q1 = (orderedData.get(q1Index) + orderedData.get(q1Index+1))/2;
            q3 = (orderedData.get(q3Index) + orderedData.get(q3Index+1))/2;
        } else {
            q1 = orderedData.get(q1Index);
            q3 = orderedData.get(q3Index);
        }
        return q3 - q1;
    }

    private static boolean isValid(int index) {
        int size = cleanedRRValues.size();
        float rr = cleanedRRValues.get(index);
        List<Float> window;
        if (index < DETECTION_WINDOW_SIZE/2) {
            window = new ArrayList<>(cleanedRRValues.subList(0, DETECTION_WINDOW_SIZE+1));
            window.remove(rr);
        } else if (index+(DETECTION_WINDOW_SIZE/2)+1 > size) {
            window = new ArrayList<>(cleanedRRValues.subList(size-DETECTION_WINDOW_SIZE-1, size));
            window.remove(rr);
        } else {
            window = new ArrayList<>(cleanedRRValues.subList(index-(DETECTION_WINDOW_SIZE/2), index+(DETECTION_WINDOW_SIZE/2)+1));
            window.remove(rr);
        }
        return rr <= (median(window) + MEDIAN_THRESHOLD); // TODO: Adjust threshold based on BPM?
    }

    private static float median(List<Float> data) {
        int size = data.size();
        if (size == 0) {
            return 0;
        }
        ArrayList<Float> orderedData = order(data);
        if (size % 2 == 0) {
            return (orderedData.get((size/2)-1) + orderedData.get((size/2)+1))/2;
        } else {
            return orderedData.get((size-1)/2);
        }
    }

    private static ArrayList<Float> order(List<Float> data) {
        ArrayList<Float> orderedData = new ArrayList<>(data.size());
        for (float f : data) {
            orderedData = insertInOrder(f, orderedData);
        }
        return orderedData;
    }

    private static ArrayList<Float> insertInOrder(float item, ArrayList<Float> list) {
        if (list.isEmpty()) {
            list.add(item);
        } else {
            int i = 0;
            while (i < list.size() && list.get(i) < item) {
                i++;
            }
            if (i < list.size() - 1) {
                list.add(i, item);
            } else {
                list.add(item);
            }
        }
        return list;
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
