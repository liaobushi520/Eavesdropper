package com.liaobusi.eavesdropper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.DeviceUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit

import java.util.regex.Matcher
import java.util.regex.Pattern


class EavesdropService : NotificationListenerService() {

    private val CHANNEL_ID = EavesdropService::class.java.simpleName
    private val CHANNEL_NAME = "EavesdropService"

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel);
        }

        startForeground(10001, getNotification())

    }

    private fun getNotification(): Notification? {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("服务运行于前台")
            .setContentText("service被设为前台进程")
            .setTicker("service正在后台运行...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(System.currentTimeMillis())
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
        val notification: Notification = builder.build()
        notification.flags = Notification.FLAG_AUTO_CANCEL
        return notification
    }


    //来通知时的调用
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val view = View(baseContext)
        view.visibility = View.INVISIBLE
        ToastUtils.showCustomShort(view)
        val notification = sbn.notification ?: return
        val extras = notification.extras
        if (extras != null) {
            //包名
            val pkg = sbn.packageName
            // 获取通知标题
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            // 获取通知内容
            val content = extras.getString(Notification.EXTRA_TEXT, "")
            //处理
            processOnReceive(pkg, title, content)
        }
    }

    /**
     * 消息来时处理
     *
     * @param pkg
     * @param title
     * @param content
     */
    private fun processOnReceive(
        pkg: String,
        title: String,
        content: String
    ) {


        if("com.eg.android.AlipayGphone" == pkg||"com.tencent.mm" == pkg){
            Log.e(
                "notification",
                String.format("收到通知，包名：%s，标题：%s，内容：%s", pkg, title, content)
            )
        }

        if ("com.eg.android.AlipayGphone" == pkg) {
            //支付宝
            if (checkMsgValid(
                    title,
                    content,
                    "alipay"
                ) && (parseMoneyAli(content, title)?.isNotEmpty() == true)
            ) {
                val amount = parseMoneyAli(content, title)!!.toBigDecimal()
                Log.e("notification", amount.toString())
                val sp = getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
                val id = sp.getString("zhifubao", null)
                if (id.isNullOrEmpty()) {
                    return
                }
                GlobalScope.launch(Dispatchers.IO) {
                    val tokenResponse = getApi().getToken(PhoneId(id)) ?: return@launch
                    val t=tokenResponse.string()
                    Log.e("notification",t)
                    val r = getApi().save(Info(id, amount, title + content, "alipay", t))
                    Log.i("notification", r?.string() ?: "fail")
                }
            }
        } else if ("com.tencent.mm" == pkg) {
            //微信
            if (checkMsgValid(
                    title,
                    content,
                    "wxpay"
                ) && (parseMoney(content)?.isNotEmpty() == true)
            ) {

                val amount = parseMoney(content)!!.toBigDecimal()
                val sp = getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
                val id = sp.getString("zhifubao", null)
                if (id.isNullOrEmpty()) {
                    return
                }
                GlobalScope.launch(Dispatchers.IO) {
                    val token = getApi().getToken(PhoneId(id)) ?: return@launch
                    getApi().save(
                        Info(
                           id,
                            amount,
                            content,
                            "wechatpay",
                            token.toString()
                        )
                    )
                }

            }
        }
    }

    /**
     * 解析内容字符串，提取金额
     *
     * @param content
     * @return
     */
    private fun parseMoney(content: String): String? {
        val pattern: Pattern = Pattern.compile("收款(([1-9]\\d*)|0)(\\.(\\d){0,2})?元")
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile("(([1-9]\\d*)|0)(\\.(\\d){0,2})?")
            val matchernum: Matcher = patternnum.matcher(tmp)
            if (matchernum.find()) return matchernum.group()
        }
        return null
    }

    private fun parseMoneyAli(content: String, title: String? = null): String? {
        if (content.contains("向你付款")) {
            return parseMoneyAli1(content)
        }

        if ((content.contains("立即查看余额情况") || content.contains("立即查看今日收款金额")) && title != null) {
            return parseMoneyAli2(title)
        }
        val amount=parseMoneyAli2(content)
        return amount

    }


    private fun parseMoneyAli1(content: String): String? {
        val pattern: Pattern = Pattern.compile("向你付款(([1-9]\\d*)|0)(\\.(\\d){0,2})?元")
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile("(([1-9]\\d*)|0)(\\.(\\d){0,2})?")
            val matchernum: Matcher = patternnum.matcher(tmp)
            if (matchernum.find()) return matchernum.group()
        }
        return null
    }

    private fun parseMoneyAli2(content: String): String? {
        val pattern: Pattern = Pattern.compile("成功收款(([1-9]\\d*)|0)(\\.(\\d){0,2})?元")
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile("(([1-9]\\d*)|0)(\\.(\\d){0,2})?")
            val matchernum: Matcher = patternnum.matcher(tmp)
            if (matchernum.find()) return matchernum.group()
        }
        return null
    }

    /**
     * 验证消息的合法性，防止非官方消息被处理
     *
     * @param title
     * @param content
     * @param gateway
     * @return
     */
    private fun checkMsgValid(
        title: String,
        content: String,
        gateway: String
    ): Boolean {
        if ("wxpay" == gateway) {
            //微信支付的消息格式
            //1条：标题：微信支付，内容：微信支付收款0.01元(朋友到店)
            //多条：标题：微信支付，内容：[4条]微信支付: 微信支付收款1.01元(朋友到店)
            val pattern: Pattern = Pattern.compile("^((\\[\\+?\\d+条])?微信支付:|微信支付收款)")
            val matcher: Matcher = pattern.matcher(content)
            return "微信支付" == title && matcher.find()
        } else if ("alipay" == gateway) {
            //支付宝的消息格式1，标题：支付宝通知，内容：支付宝成功收款1.00元。 格式2 标题：你已成功收款0.01元，内容：立即查看余额情况>>
            return "收款通知" == title || title.contains("收钱码") || title.contains("已成功收款")
        }
        return false
    }
}