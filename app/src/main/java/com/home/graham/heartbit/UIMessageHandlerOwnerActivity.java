package com.home.graham.heartbit;

import android.app.Activity;
import android.os.Handler;

public interface UIMessageHandlerOwnerActivity {
    public Handler getUIMessageHandler();
    public Activity getActivity();
}
