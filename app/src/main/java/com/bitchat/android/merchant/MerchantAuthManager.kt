package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import com.bitchat.android.identity.SecureIdentityStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson

/**
 * Manages merchant authentication state and secure storage
 * Uses SecureIdentityStateManager for encrypted storage
 */
class MerchantAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MerchantAuthManager"
        private const val KEY_MERCHANT_TOKEN = "merchant_auth_token"
        private const val KEY_MERCHANT_USER = "merchant_user_data"
        private const val KEY_IS_MERCHANT_LOGGED_IN = "is_merchant_logged_in"
        
        @Volatile
        private var INSTANCE: MerchantAuthManager? = null
        
        fun getInstance(context: Context): MerchantAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MerchantAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val secureStorage = SecureIdentityStateManager(context)
    private val gson = Gson()
    
    // Authentication state flows
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _currentUser = MutableStateFlow<MerchantUser?>(null)
    val currentUser: StateFlow<MerchantUser?> = _currentUser.asStateFlow()
    
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()
    
    init {
        // Load existing authentication state
        loadAuthenticationState()
    }
    
    /**
     * Load authentication state from secure storage
     */
    private fun loadAuthenticationState() {
        try {
            val isLoggedIn = secureStorage.getSecureValue(KEY_IS_MERCHANT_LOGGED_IN)?.toBoolean() ?: false
            val token = secureStorage.getSecureValue(KEY_MERCHANT_TOKEN)
            val userJson = secureStorage.getSecureValue(KEY_MERCHANT_USER)
            
            if (isLoggedIn && token != null && userJson != null) {
                val user = try { gson.fromJson(userJson, MerchantUser::class.java) } catch (e: Exception) { null }
                
                if (user != null) {
                    _isLoggedIn.value = true
                    _authToken.value = token
                    _currentUser.value = user
                    Log.d(TAG, "Loaded merchant authentication state for user: ${user.name}")
                } else {
                    Log.w(TAG, "Stored merchant user JSON failed to parse")
                }
            } else {
                Log.d(TAG, "No existing merchant authentication found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load authentication state", e)
            clearAuthenticationState()
        }
    }
    
    /**
     * Store successful login data
     */
    fun storeAuthenticationData(loginData: MerchantLoginData) {
        try {
            val userJson = gson.toJson(loginData.user)
            
            secureStorage.storeSecureValue(KEY_IS_MERCHANT_LOGGED_IN, "true")
            secureStorage.storeSecureValue(KEY_MERCHANT_TOKEN, loginData.token)
            secureStorage.storeSecureValue(KEY_MERCHANT_USER, userJson)
            
            _isLoggedIn.value = true
            _authToken.value = loginData.token
            _currentUser.value = loginData.user
            
            Log.d(TAG, "Stored merchant authentication data for user: ${loginData.user.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store authentication data", e)
        }
    }
    
    /**
     * Clear authentication state (logout)
     */
    fun clearAuthenticationState() {
        try {
            secureStorage.clearSecureValues(
                KEY_IS_MERCHANT_LOGGED_IN,
                KEY_MERCHANT_TOKEN,
                KEY_MERCHANT_USER
            )
            
            _isLoggedIn.value = false
            _authToken.value = null
            _currentUser.value = null
            
            Log.d(TAG, "Cleared merchant authentication state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear authentication state", e)
        }
    }
    
    /**
     * Get authorization header for API requests
     */
    fun getAuthorizationHeader(): String? {
        return _authToken.value?.let { "Bearer $it" }
    }
    
    /**
     * Check if current token is valid (basic check)
     */
    fun hasValidToken(): Boolean {
        return _isLoggedIn.value && !_authToken.value.isNullOrBlank()
    }
    
    /**
     * Get current merchant user info
     */
    fun getCurrentUser(): MerchantUser? {
        return _currentUser.value
    }
    
    /**
     * Logout merchant
     */
    fun logout() {
        Log.d(TAG, "Merchant logout requested")
        clearAuthenticationState()
    }
}