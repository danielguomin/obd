package com.mapbar.adas;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.miyuan.obd.R;

import java.util.Timer;
import java.util.TimerTask;

@PageSetting(contentViewId = R.layout.installation_guide_layout)
public class InstallationGuidePage extends AppBasePage implements View.OnClickListener {

    @ViewInject(R.id.first)
    TextView firstTV;
    @ViewInject(R.id.second)
    TextView secondTV;
    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.confirm)
    private TextView confirmV;
    private Timer timer = new Timer();
    private TimerTask timerTask;
    private int time = 10;
    private boolean ishow;
    private boolean timeOut;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        title.setText("安装引导");
        reportV.setVisibility(View.GONE);
        back.setVisibility(View.GONE);
        firstTV.setText(Html.fromHtml("<font color='#009488'>请您准备好以下工具:<br><br>1、手机</font><br><font color='#4A4A4A'>注册收取验证码.</font><br><br>"));
        secondTV.setText(Html.fromHtml("<font color='#009488'>2、授权码</font><br><font color='#4A4A4A'>HUD产品在产品背面,OBD系列产品在包装上.</font>"));
        if (!ishow) {
            confirmV.setEnabled(ishow);
            ishow = true;
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    GlobalUtil.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (time <= 0 && timer != null) {
                                timer.cancel();
                                timer = null;
                                timerTask.cancel();
                                timerTask = null;
                                timeOut = true;
                                confirmV.setText("我已经准备好了");
                                confirmV.setEnabled(true);
                                confirmV.setOnClickListener(InstallationGuidePage.this);
                            } else {
                                confirmV.setText("我已经准备好了(" + time + "s)");
                            }
                            time--;
                        }
                    });
                }
            };
            timer.schedule(timerTask, 0, 1000);
        } else {
            if (timeOut) {
                confirmV.setEnabled(ishow);
                confirmV.setOnClickListener(this);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (null != timerTask) {
            timerTask.cancel();
            timerTask = null;
            timer.cancel();
            timer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back:
                PageManager.back();
                break;
            case R.id.confirm:
                AuthPage authPage = new AuthPage();
                Bundle bundle = new Bundle();
                bundle.putString("boxId", getDate().getString("boxId"));
                authPage.setDate(bundle);
                PageManager.go(authPage);
                break;
        }
    }

}
