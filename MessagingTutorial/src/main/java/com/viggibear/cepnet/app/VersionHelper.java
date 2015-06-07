package com.viggibear.cepnet.app;
import android.app.Activity;

class VersionHelper
{
    static void refreshActionBarMenu(Activity activity)
    {
        activity.invalidateOptionsMenu();
    }
}