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
}
