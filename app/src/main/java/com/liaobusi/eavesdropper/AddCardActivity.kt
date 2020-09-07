package com.liaobusi.eavesdropper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_add_card.*
import kotlinx.coroutines.*
import java.math.BigDecimal

class AddCardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_card)
        save.setOnClickListener {
            val bankName = bankNameEdt.editableText.toString()
            val alipayAccount = alipayNoEdt.editableText.toString()
            val bankAccount = bankAccountEdt.editableText.toString()
            val userName = userNameEdt.editableText.toString()
            val singleMin =
                singleMinEdt.editableText.toString().toBigDecimalOrNull() ?: BigDecimal(0)
            val singleMax = singleMaxEdt.editableText.toString().toBigDecimalOrNull() ?: BigDecimal(
                Int.MAX_VALUE
            )
            val remark = remarkEdt.editableText.toString()
            val singleDayCount = singleDayCountEdt.editableText.toString().toIntOrNull()
            val singleDayAmount = singleDayAmountEdt.editableText.toString().toBigDecimalOrNull()

            if (bankName.isEmpty()) {
                bankNameLayout.error = "银行名称必填"
                return@setOnClickListener
            }
            if (alipayAccount.isEmpty()) {
                alipayNoLayout.error = "支付宝账号必填"
                return@setOnClickListener
            }
            if (bankAccount.isEmpty()) {
                bankAccountLayout.error = "银行卡号必填"
                return@setOnClickListener
            }

            if (userName.isEmpty()) {
                userNameLayout.error = "用户名必填"
                return@setOnClickListener
            }

            GlobalScope.launch(Dispatchers.Main) {
               launch(Dispatchers.IO) {
                    val request = AddCardRequest(
                        userName = userName,
                        phoneId = alipayAccount,
                        accountNo = bankAccount,
                        accountName = bankName,
                        remark = remark,
                        singleDayAmount = singleDayAmount,
                        singleDayCount = singleDayCount,
                        singleMin = singleMin,
                        singleMax = singleMax
                    )
                   try {
                       val response = getApi().addCard(request)
                       if(response!=null){
                           ToastUtils.showLong("成功添加银行卡")
                       }
                   }catch (e:Throwable){
                       ToastUtils.showLong(e.message?:"未知错误")
                   }

               }

            }

        }
    }


}