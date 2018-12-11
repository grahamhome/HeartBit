package com.home.graham.heartbit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class UserData {

    private static SharedPreferences getUserData(Activity invoker) {
        return invoker.getSharedPreferences("User_Data", Context.MODE_PRIVATE);
    }

    static void setIntroViewed(Activity invoker) {
        getUserData(invoker).edit().putBoolean("Intro_Viewed", true).apply();
    }

    static boolean getIntroViewed(Activity invoker) {
        return getUserData(invoker).getBoolean("Intro_Viewed", false);
    }

    static boolean getDemographicsEntered(Activity invoker) {
        return getUserData(invoker).getBoolean("Demographics_Entered", false);
    }

    static void setIntructionsViewed(Activity invoker) {
        getUserData(invoker).edit().putBoolean("Instructions_Viewed", true).apply();
    }

    static boolean getInstructionsViewed(Activity invoker) {
        return getUserData(invoker).getBoolean("Instructions_Viewed", false);
    }

    static void setMonitorID(String id, Activity invoker) {
        getUserData(invoker).edit().putString("Monitor_ID", id).apply();
    }

    static String getMonitorID(Activity invoker) {
        return getUserData(invoker).getString("Monitor_ID", "no-monitor-found");
    }

    static void setBreathingConfig(boolean participantMode, float breathIn, float breathOut, int sessionLength, Boolean inOutPaired, Boolean participantSettingsEnabled, Activity invoker) {
        SharedPreferences.Editor researcherBREditor = getUserData(invoker).edit();
        researcherBREditor.putFloat(participantMode ? "Participant_BR_In" : "Researcher_BR_In", breathIn);
        researcherBREditor.putFloat(participantMode ? "Participant_BR_Out" : "Researcher_BR_Out", breathOut);
        researcherBREditor.putInt(participantMode ? "Participant_Session_Length" : "Researcher_Session_Length", sessionLength);
        researcherBREditor.putBoolean(participantMode ? "Participant_In_Out_Paired" : "Researcher_In_Out_Paired", inOutPaired);
        researcherBREditor.putBoolean("Participant_Settings_Enabled", participantSettingsEnabled);
        researcherBREditor.apply();
    }

    /**
     * Returns the breathe-in time in sec
     */
    static float getBRIn(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getFloat(participantMode ? "Participant_BR_In" : "Researcher_BR_In", 5.0f);
    }

    /**
     * Returns the breathe-out time in sec
     */
    static float getBROut(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getFloat(participantMode ? "Participant_BR_Out" : "Researcher_BR_Out", 5.0f);
    }

    /**
     * Returns the session time in mins
     */
    static int getSessionLength(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getInt(participantMode ? "Participant_Session_Length" : "Researcher_Session_Length", 3);
    }

    static boolean getInOutPaired(boolean participantMode, Activity invoker) {
        return getUserData(invoker).getBoolean(participantMode ? "Participant_In_Out_Paired" : "Researcher_In_Out_Paired", true);
    }

    static boolean getParticipantSettingsEnabled(Activity invoker) {
        return getUserData(invoker).getBoolean("Participant_Settings_Enabled", true);
    }

    static void setDemographicData(Activity invoker, String dob, String weight, String gender, String race, String smoker) {
        SharedPreferences.Editor demographicsEditor = getUserData(invoker).edit();
        demographicsEditor.putString("dob", dob);
        demographicsEditor.putString("weight", weight);
        demographicsEditor.putString("gender", gender);
        demographicsEditor.putString("race", race);
        demographicsEditor.putString("smoker", smoker);
        demographicsEditor.putBoolean("Demographics_Entered", true).apply();
    }

    static String getDemographicDataCSV(Activity invoker) {
        SharedPreferences prefs = getUserData(invoker);
        return new StringBuilder().append("DOB,Weight (lbs),Gender,Race,Smoker").append("\r\n")
                .append(prefs.getString("dob","")).append(",")
                .append(prefs.getString("weight", "")).append(",")
                .append(prefs.getString("gender", "")).append(",")
                .append(prefs.getString("race", "")).append(",")
                .append(prefs.getString("smoker", "")).toString();
    }

    public static void setDemographicsUploaded(Activity invoker) {
        getUserData(invoker).edit().putBoolean("demographicsUploaded", true).apply();
    }

    public static boolean getDemographicsUploaded(Activity invoker) {
        return getUserData(invoker).getBoolean("demographicsUploaded", false);
    }

    public static int getNumberOfTrials(Activity invoker) {
        return getUserData(invoker).getInt("numberTrials", 0);
    }

    public static void incrementTrialCount(Activity invoker) {
        SharedPreferences prefs = getUserData(invoker);
        prefs.edit().putInt("numberTrials", prefs.getInt("numberTrials", 0)+1).apply();
    }

}
