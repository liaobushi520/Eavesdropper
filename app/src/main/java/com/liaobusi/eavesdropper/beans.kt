package com.liaobusi.eavesdropper

import java.math.BigDecimal

data class PhoneId(val phoneId: String)
data class Info(
    val phoneId: String,
    val amount: BigDecimal,
    val msg: String,
    val type: String,
    val authToken: String
)

data class AddCardRequest(
    val phoneId: String,
    val accountNo: String,
    val accountName: String,
    val userName: String,
    val singleMin: BigDecimal?,
    val singleMax: BigDecimal?,
    val singleDayCount: Int?,
    val singleDayAmount: BigDecimal?,
    val remark: String
)

data class CardPayRequest(
    val accountNo :String?=null ,
    val accountBank:String?=null,
    val amount: BigDecimal?=null,
    val attr:String
)