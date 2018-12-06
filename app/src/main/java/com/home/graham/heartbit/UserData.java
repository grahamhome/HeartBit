package com.home.graham.heartbit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class UserData {

    private static SharedPreferences getUserData(Activity invoker) {
        return invoker.getSharedPreferences("User_Data", Context.MODE_PRIVATE);
    }

    public static void setName(String name, Activity invoker) {
        SharedPreferences.Editor nameEditor = getUserData(invoker).edit();
        nameEditor.putString("Name", name);
        nameEditor.apply();
    }

    public static String getName(Activity invoker) {
        return getUserData(invoker).getString("Name", "User");
    }

    public static void setIntroViewed(boolean viewed, Activity invoker) {
        SharedPreferences.Editor introViewedEditor = getUserData(invoker).edit();
        introViewedEditor.putBoolean("Intro_Viewed", viewed);
        introViewedEditor.apply();
    }

    public static boolean getIntroViewed(Activity invoker) {
        return getUserData(invoker).getBoolean("Intro_Viewed", false);
    }

    public static void setIntructionsViewed(boolean viewed, Activity invoker) {
        SharedPreferences.Editor instructionsViewedEditor = getUserData(invoker).edit();
        instructionsViewedEditor.putBoolean("Instructions_Viewed", viewed);
        instructionsViewedEditor.apply();
    }

    public static boolean getInstructionsViewed(Activity invoker) {
        return getUserData(invoker).getBoolean("Instructions_Viewed", false);
    }

    public static void setMonitorID(String id, Activity invoker) {
        SharedPreferences.Editor monitorIDEditor = getUserData(invoker).edit();
        monitorIDEditor.putString("Monitor_ID", id);
        monitorIDEditor.apply();
    }

    public static String getMonitorID(Activity invoker) {
        return getUserData(invoker).getString("Monitor_ID", "no-monitor-found");
    }

    public static void setBreathingConfig(boolean participantMode, int breathIn, int breathOut, int sessionLength, Boolean inOutPaired, Boolean participantSettingsEnabled, Activity invoker) {
        SharedPreferences.Editor researcherBREditor = getUserData(invoker).edit();
        researcherBREditor.putInt(participantMode ? "Participant_BR_In" : "Researcher_BR_In", breathIn);
        researcherBREditor.putInt(participantMode ? "Participant_BR_Out" : "Researcher_BR_Out", breathOut);
        researcherBREditor.putInt(participantMode ? "Participant_Session_Length" : "Researcher_Session_Length", sessionLength);
        researcherBREditor.putBoolean(participantMode ? "Participant_In_Out_Paired" : "Researcher_In_Out_Paired", inOutPaired);
        researcherBREditor.putBoolean("Participant_Settings_Enabled", participantSettingsEnabled);
        researcherBREditor.apply();
    }

    public static Integer getBRIn(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getInt(participantMode ? "Participant_BR_In" : "Researcher_BR_In", 5000);
    }

    public static Integer getBROut(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getInt(participantMode ? "Participant_BR_Out" : "Researcher_BR_Out", 5000);
    }

    public static Integer getSessionLength(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getInt(participantMode ? "Participant_Session_Length" : "Researcher_Session_Length", 180000);
    }

    public static Boolean getInOutPaired(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getBoolean(participantMode ? "Participant_In_Out_Paired" : "Researcher_In_Out_Paired", false);
    }

    public static Boolean getParticipantSettingsEnabled(Activity invoker) {
        return getUserData(invoker).getBoolean("Participant_Settings_Enabled", true);
    }

}
