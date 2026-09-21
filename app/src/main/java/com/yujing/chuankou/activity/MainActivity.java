package com.yujing.chuankou.activity;

import android.content.Intent;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityModeBinding;

public class MainActivity extends BaseActivity<ActivityModeBinding> {
    public MainActivity() {
        super(R.layout.activity_mode);
    }

    @Override
    protected void init() {
        binding.cardSerial.setOnClickListener(v ->
                startActivity(new Intent(this, SerialAssistantActivity.class)));
        binding.cardUdp.setOnClickListener(v ->
                startActivity(new Intent(this, UdpAssistantActivity.class)));
        binding.cardBridge.setOnClickListener(v ->
                startActivity(new Intent(this, SerialUdpBridgeActivity.class)));
    }
}
