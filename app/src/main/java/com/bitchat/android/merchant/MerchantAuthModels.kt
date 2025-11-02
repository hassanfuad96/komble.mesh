package com.bitchat.android.merchant

/**
 * Request model for merchant login
 */
data class MerchantLoginRequest(
    val email: String,
    val password: String
)

/**
 * User data from login response
 */
data class MerchantUser(
    val id: Int,
    val sub_account_id: Int? = null,
    val upline_id: Int? = null,
    val name: String,
    val email: String,
    val phone: String,
    val country_code: String? = null,
    val currency: String,
    val timezone: String? = null
)

/**
 * Data payload from login response
 */
data class MerchantLoginData(
    val token: String,
    val user: MerchantUser
)

/**
 * Complete login response model
 */
data class MerchantLoginResponse(
    val result: Boolean,
    val message: String,
    val data: MerchantLoginData? = null
)