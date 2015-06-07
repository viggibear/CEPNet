package com.viggibear.cepnet.app;

import android.app.Application;
import com.parse.Parse;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Parse.initialize(this, "JIlJ16fgPMUW06SfEUFW3R16LZ863gWH72yYway8", "n97cOErhfOPnxUsN5ltijfVqPQ8ZEXdg65nvIZho");
    }
}
