package com.home.graham.heartbit;

import android.media.ToneGenerator;

import com.karlotoy.perfectune.instance.PerfectTune;

public class Tone extends Thread {
    private PerfectTune generator;

    private static double toneIn = 329.63;
    private static double toneOut = 261.63;

    private boolean playing = false;

    public Tone() {
        run();
    }

    @Override
    public void run() {
        generator = new PerfectTune();
    }

    public void start() {
        generator.setTuneFreq(toneIn);
        generator.playTune();
    }

    public void end() {
        generator.stopTune();
    }

    public void changeTone(boolean in) {
        generator.setTuneFreq(in ? toneIn : toneOut);
    }

}
