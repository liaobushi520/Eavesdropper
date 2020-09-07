package com.liaobusi.eavesdropper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsMessage
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.regex.Matcher
import java.util.regex.Pattern

data class BankSmsTemplate(
    val phone: String,
    val bankName: String,
    val patternText: List<String>,
    val bankNoPatternText: String,
    val bankNoRegex: String=MATCH_BANK_NO_TEXT
)

const val MATCH_MONEY_TEXT = "(([1-9]\\d*)|0)(\\.(\\d){0,2})?"
const val MATCH_BANK_NO_TEXT = "([0-9]{4})"
const val MATCH_BANK_NO_TEXT2 = "([0-9]{3})"

val banks = mapOf(
    "95580" to BankSmsTemplate(
        patternText = listOf("提现金额${MATCH_MONEY_TEXT}元"),
        bankName = "邮政银行", phone = "95580"
        , bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT2}账户",
        bankNoRegex=MATCH_BANK_NO_TEXT2
    ),
    "95588" to BankSmsTemplate(
        patternText = listOf("转账支付宝\\)${MATCH_MONEY_TEXT}元"),
        bankName = "工商银行", phone = "95588"
        , bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}"
    ),
    "95599" to BankSmsTemplate(
        patternText = listOf("代付交易人民币${MATCH_MONEY_TEXT}"),
        bankName = "中国农业银行", phone = "95599", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}账户"
    ),
    "95508" to BankSmsTemplate(
        patternText = listOf("收入人民币${MATCH_MONEY_TEXT}元"),
        bankName = "广发银行", phone = "95508", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}"
    ),
    "1069800096511" to BankSmsTemplate(
        patternText = listOf("付款存入${MATCH_MONEY_TEXT}元"),
        bankName = "长沙银行", phone = "1069800096511", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}的人民币"
    ),
    "95533" to BankSmsTemplate(
        patternText = listOf("付款存入${MATCH_MONEY_TEXT}元", "收入人民币${MATCH_MONEY_TEXT}元"),
        bankName = "建设银行", phone = "95533", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}的"
    ),
    "桂林银行" to BankSmsTemplate(
        patternText = listOf("收入\\(支付宝转入\\)${MATCH_MONEY_TEXT}元"),
        bankName = "桂林银行", phone = "95533", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}账户"
    ),
    "95528" to BankSmsTemplate(
        patternText = listOf("存入${MATCH_MONEY_TEXT}"),
        bankName = "浦发银行", phone = "95528", bankNoPatternText = "您尾号${MATCH_BANK_NO_TEXT}卡"
    ),
    "95566" to BankSmsTemplate(
        patternText = listOf("收入人民币${MATCH_MONEY_TEXT}"),
        bankName = "中国银行", phone = "95566", bankNoPatternText = ""
    ),
    "95559" to BankSmsTemplate(
        patternText = listOf("转入${MATCH_MONEY_TEXT}元"),
        bankName = "交通银行", phone = "95559", bankNoPatternText = "您尾号*${MATCH_BANK_NO_TEXT}的卡"
    ),
    "95561" to BankSmsTemplate(
        patternText = listOf("付款收入${MATCH_MONEY_TEXT}元"),
        bankName = "兴业银行", phone = "95561", bankNoPatternText = "账户*${MATCH_BANK_NO_TEXT}*"
    ),
    "95595" to BankSmsTemplate(
        patternText = listOf("存入${MATCH_MONEY_TEXT}元"),
        bankName = "光大银行", phone = "95595", bankNoPatternText = "尾号${MATCH_BANK_NO_TEXT}账户"
    ),
    "1069070996599" to BankSmsTemplate(
        patternText = listOf("来账存入${MATCH_MONEY_TEXT}元"),
        bankName = "华融湘江银行", phone = "1069070996599", bankNoPatternText = "尾号为${MATCH_BANK_NO_TEXT}的账户"
    ),
    "95568" to BankSmsTemplate(
        patternText = listOf("存入￥${MATCH_MONEY_TEXT}元"),
        bankName = "民生银行", phone = "95568", bankNoPatternText = "账户*${MATCH_BANK_NO_TEXT}"
    )

)

private fun parseBank(content: String, regexs: List<String>): String? {
    for (regex in regexs) {
        val pattern: Pattern = Pattern.compile(regex)
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile(MATCH_MONEY_TEXT)
            val matchernum: Matcher = patternnum.matcher(tmp)
            if (matchernum.find()) return matchernum.group()
        }
    }
    return null
}

private fun parseBankNo(content: String, regex: String,bankNoRegex:String= MATCH_BANK_NO_TEXT): String? {
    val pattern: Pattern = Pattern.compile(regex)
    val matcher: Matcher = pattern.matcher(content)
    if (matcher.find()) {
        val tmp: String = matcher.group()
        val patternnum: Pattern = Pattern.compile(bankNoRegex)
        val matchernum: Matcher = patternnum.matcher(tmp)
        if (matchernum.find()) return matchernum.group()
    }
    return null
}


class EavesdropService : NotificationListenerService() {

    private val CHANNEL_ID = EavesdropService::class.java.simpleName
    private val CHANNEL_NAME = "EavesdropService"

    private val smsObserver: SmsObserver = SmsObserver(Handler())

    override fun onCreate() {
        super.onCreate()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel);
        }

        startForeground(10001, getNotification())
        contentResolver.registerContentObserver(Sms.CONTENT_URI, true, smsObserver)
        GlobalScope.launch {
            while (true) {
                toggleNotificationListenerService()
                delay(60000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(smsObserver)
    }


    private fun toggleNotificationListenerService() {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            ComponentName(
                this,
                EavesdropService::class.java
            ),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            ComponentName(
                this,
                EavesdropService::class.java
            ),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
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

        if ("com.eg.android.AlipayGphone" == pkg || "com.tencent.mm" == pkg) {
            Log.i("notification", "收到通知，包名：${pkg}，标题：$title，内容：$content")
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
                val sp = getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
                val id = sp.getString("zhifubao", null)
                if (id.isNullOrEmpty()) {
                    return
                }
                try {
                    GlobalScope.launch(Dispatchers.IO) {
                        val tokenResponse = getApi().getToken(PhoneId(id)) ?: return@launch
                        val t = tokenResponse.string()
                        Log.e("notification", t)
                        val r = getApi().save(Info(id, amount, "$title: $content", "alipay", t))
                        Log.i("notification", r?.string() ?: "fail")
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
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
        val pattern: Pattern = Pattern.compile("收款${MATCH_MONEY_TEXT}元")
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile(MATCH_MONEY_TEXT)
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
        val amount = parseMoneyAli2(content)
        return amount

    }


    private fun parseMoneyAli1(content: String): String? {
        val pattern: Pattern = Pattern.compile("向你付款${MATCH_MONEY_TEXT}元")
        val matcher: Matcher = pattern.matcher(content)
        var matchStart = 0
        var tmp = ""
        while (matcher.find(matchStart)) {
            tmp = matcher.group()
            matchStart = matcher.end()
        }
        if (tmp.isEmpty()) {
            return null
        }

        val patternnum: Pattern = Pattern.compile(MATCH_MONEY_TEXT)
        val matchernum: Matcher = patternnum.matcher(tmp)
        if (matchernum.find())
            return matchernum.group()

        return null
    }

    private fun parseMoneyAli2(content: String): String? {
        val pattern: Pattern = Pattern.compile("成功收款${MATCH_MONEY_TEXT}元")
        val matcher: Matcher = pattern.matcher(content)
        if (matcher.find()) {
            val tmp: String = matcher.group()
            val patternnum: Pattern = Pattern.compile(MATCH_MONEY_TEXT)
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


    inner class SmsObserver(handler: Handler) : ContentObserver(handler) {

        private val TAG = "sms observer"
        private val pendingContent = mutableListOf<String>()

        override fun onChange(selfChange: Boolean, uri: Uri) {
            val sp = getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
            if (!sp.getBoolean("enable_sms", false)) {
                return
            }
            Log.i(TAG, "===$uri==")
            if (!uri.toString().contains("content://sms/inbox/")) {
                return;
            }

            val cursor = contentResolver.query(uri, null, null, null, Sms.DEFAULT_SORT_ORDER)
                ?: return
            if (cursor.moveToFirst()) {
                Log.i("sms", "$uri")
                val address = cursor.getString(cursor.getColumnIndex(Sms.ADDRESS))//"95533"
                val content = cursor.getString(cursor.getColumnIndex(Sms.BODY))//"您尾号1212的收入人民币700元"
                if (pendingContent.contains(content)) {
                    return
                }
                Log.i(
                    TAG,
                    "address:${address} content:${content}"
                )
                val template = banks[address] ?: return
                val amount = parseBank(content, template.patternText)
                val accountNo = parseBankNo(content, template.bankNoPatternText,template.bankNoRegex)
                if (amount.isNullOrEmpty() || accountNo.isNullOrEmpty()) {
                    return
                }
                Log.i(TAG, "amount $amount  , accountNo $accountNo")
                pendingContent.add(content)
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val tokenResponse = getApi().cardPay(
                            CardPayRequest(
                                accountBank = template.bankName,
                                attr = content,
                                accountNo = accountNo,
                                amount = BigDecimal(amount)
                            )
                        )
                        if (tokenResponse != null) {
                            Log.i("sms", "成功${tokenResponse}")
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                    delay(3000)
                    pendingContent.remove(content)
                }
            }
        }
    }


}


class SmsBroadcastReceiver : BroadcastReceiver() {
    private val pendingContent = mutableListOf<String>()
    private val TAG = "sms receiver"
    override fun onReceive(context: Context?, intent: Intent?) {
        val sp = context!!.getSharedPreferences("eavesdropper", Context.MODE_PRIVATE)
        if (!sp.getBoolean("enable_sms2", false)) {
            return
        }
        if (intent?.action?.equals(SMS_RECEIVED_ACTION)!!) {
            val message = getMessagesFromIntent(intent)
            Log.i(
                TAG, message.address + " : " +
                        message.content + " : "
            )
            val address =  message.address
            val content = message.content
            if (address.isNullOrEmpty() || content.isNullOrEmpty()) {
                return
            }
            val template = banks[address] ?: return
            val amount = parseBank(content, template.patternText)
            val accountNo = parseBankNo(content, template.bankNoPatternText,template.bankNoRegex)
            if (amount.isNullOrEmpty() || accountNo.isNullOrEmpty()) {
                return
            }
            Log.i(TAG, "amount $amount  , accountNo $accountNo")
            pendingContent.add(content)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val tokenResponse = getApi().cardPay(
                        CardPayRequest(
                            accountBank = template.bankName,
                            attr = content,
                            accountNo = accountNo,
                            amount = BigDecimal(amount)
                        )
                    )
                    if (tokenResponse != null) {
                        Log.i(TAG, "成功${tokenResponse}")
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                delay(3000)
                pendingContent.remove(content)
            }


        }
    }

    data class Sms(val address: String, val content: String)


    private fun getMessagesFromIntent(intent: Intent): Sms {


        val content = StringBuilder() //用于存储短信内容

        var sender = "" //存储短信发送方手机号

        val bundle: Bundle? = intent.extras //通过getExtras()方法获取短信内容

        val format = intent.getStringExtra("format")
        if (bundle != null) {
            val pdusArray = bundle.get("pdus") as Array<Any>;
            for (pdus in pdusArray) {
                val message =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(
                            pdus as ByteArray,
                            format
                        )
                    } else {
                        SmsMessage.createFromPdu(
                            pdus as ByteArray
                        )
                    } //将字节数组转化为Message对象
                sender = message.originatingAddress ?: "" //获取短信手机号
                content.append(message.messageBody) //获取短信内容
            }
        }

        return Sms(sender, content.toString())

    }


}