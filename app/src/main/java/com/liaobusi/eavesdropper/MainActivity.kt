package com.liaobusi.eavesdropper

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.os.EnvironmentCompat
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ToastUtils
import com.tencent.wcdb.database.SQLiteCipherSpec
import com.tencent.wcdb.database.SQLiteDatabase
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Permission


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


        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
            ),
            1
        )


        startService(intent)
        text.text = sp.getString("zhifubao", "未设置")
        save.setOnClickListener {
            val input = et.editableText.toString()
            if (input.isNotEmpty()) {
                sp.edit().putString("zhifubao", input).apply()
                text.text = input
            }
        }

        smsSwitch.isChecked = sp.getBoolean("enable_sms", false)
        smsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("enable_sms", isChecked).apply()
        }

        smsSwitch2.isChecked = sp.getBoolean("enable_sms2", false)
        smsSwitch2.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("enable_sms2", isChecked).apply()
        }

    }


    // 判断是否打开了通知监听权限
    private fun isEnabled(): Boolean {
        val pkgName = packageName;
        val flat =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
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


    private fun openNotificationListenSettings() {
        try {
            val notificationIntent =
                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
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
