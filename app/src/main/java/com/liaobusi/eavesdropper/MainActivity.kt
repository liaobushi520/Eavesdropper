package com.liaobusi.eavesdropper

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Window
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        val sp = getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
        val intent = Intent(this, EavesdropService::class.java)
        if (!isEnabled()) {
            openNotificationListenSettings()
            ToastUtils.showLong("请打开Eavesdropper读取通知的权限")
        }

        startService(intent)
        text.text = sp.getString("zhifubao", "未设置")
        save.setOnClickListener {
            val input = et.editableText.toString()
            if (input.isNotEmpty()) {
                sp.edit().putString("zhifubao", input).commit()
                text.text = input
            }
        }
        Log.e("eavesdrop","init")

    }


    // 判断是否打开了通知监听权限
    private fun isEnabled(): Boolean {
        val pkgName = packageName;
        val flat =
            Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":");
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    fun openNotificationListenSettings() {
        try {
            val notificationIntent =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                } else {
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                }
            startActivity(notificationIntent)
        } catch (e: Exception) {
            e.printStackTrace();
        }
    }
}
