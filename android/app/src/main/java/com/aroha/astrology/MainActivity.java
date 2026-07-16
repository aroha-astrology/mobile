package com.aroha.astrology;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PlayBillingPlugin.class);
        registerPlugin(TtsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
