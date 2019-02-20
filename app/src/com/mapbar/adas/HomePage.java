package com.mapbar.adas;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gyf.barlibrary.ImmersionBar;
import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.mapbar.hamster.BleCallBackListener;
import com.mapbar.hamster.BlueManager;
import com.mapbar.hamster.OBDEvent;
import com.mapbar.hamster.OBDStatusInfo;
import com.mapbar.hamster.core.ProtocolUtils;
import com.miyuan.obd.R;

@PageSetting(contentViewId = R.layout.home_layout, flag = BasePage.FLAG_SINGLE_TASK)
public class HomePage extends AppBasePage implements View.OnClickListener, BleCallBackListener {
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.title_text)
    private TextView title;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.trie)
    private View trieV;
    @ViewInject(R.id.fault)
    private View faultV;
    @ViewInject(R.id.physical)
    private View physicalV;
    @ViewInject(R.id.dash)
    private View dashV;
    @ViewInject(R.id.message)
    private View messageV;
    @ViewInject(R.id.hud)
    private View hudV;
    private OBDStatusInfo obdStatusInfo;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        BlueManager.getInstance().addBleCallBackListener(this);
        BlueManager.getInstance().send(ProtocolUtils.checkMatchingStatus());
        back.setVisibility(View.GONE);
        reportV.setVisibility(View.GONE);
        trieV.setOnClickListener(this);
        faultV.setOnClickListener(this);
        physicalV.setOnClickListener(this);
        dashV.setOnClickListener(this);
        messageV.setOnClickListener(this);
        hudV.setOnClickListener(this);
        title.setText("汽车卫士");
        ImmersionBar.with(MainActivity.getInstance())
                .fitsSystemWindows(true)
                .statusBarDarkFont(true)
                .statusBarColor(MainActivity.getInstance().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? android.R.color.black : R.color.main_title_color)
                .init(); //初始化，默认透明状态栏和黑色导航栏
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.trie:

                if (null != obdStatusInfo && !obdStatusInfo.getpVersion().startsWith("0000")) {
                    MainPage mainPage = new MainPage();
                    Bundle mainBundle = new Bundle();
                    mainBundle.putSerializable("obdStatusInfo", obdStatusInfo);
                    mainPage.setDate(mainBundle);
                    PageManager.go(mainPage);
                } else {
                    Toast.makeText(getContext(), "此硬件不支持胎压功能！", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.fault:
                PageManager.go(new FaultReadyPage());
                break;
            case R.id.physical:
                PageManager.go(new PhysicalReadyPage());
                break;
            case R.id.dash:
                PageManager.go(new DashBoardPage());
                break;
            case R.id.message:
                break;
            case R.id.hud:
                PageManager.go(new HUDPage());
                break;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        BlueManager.getInstance().removeCallBackListener(this);
    }

    @Override
    public boolean onBackPressed() {
        PageManager.finishActivity(MainActivity.getInstance());
        return true;
    }

    @Override
    public void onEvent(int event, Object data) {
        switch (event) {
            case OBDEvent.NORMAL:
                obdStatusInfo = (OBDStatusInfo) data;
                break;
        }
    }
}
