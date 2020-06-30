package com.liaobusi.eavesdropper

import java.math.BigDecimal

data class PhoneId(val phoneId:String)
data class Info(val phoneId:String,val amount:BigDecimal,val msg:String,val type:String,val authToken:String)