package com.mycompany.data.repository

import com.mycompany.BuildConfig
import com.mycompany.data.Resource
import com.mycompany.data.api.API
import com.mycompanydata.managers.SessionManager
import com.mycompany.data.model.CHECK_YOUR_FIELDS
import com.mycompany.data.model.NETWORK_ERROR
import com.mycompany.data.model.login.LoginRequest
import com.mycompany.data.model.login.LoginResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Credentials
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class LoginRepository @Inject constructor(ioCoroutineContext: CoroutineContext, retrofitApi: API, sessionManager: SessionManager) {

    private val baseRetrofitApi = retrofitApi

    private val mCoroutineContext = ioCoroutineContext
    private val mSessionManager = sessionManager

    private fun getRetrofitApi(): API{
        return if(mSessionManager.getIsDevMode()){
            Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_DEVELOPMENT_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(API::class.java)
        }else{
            baseRetrofitApi
        }
    }


    suspend fun doLogin(loginRequest: LoginRequest): Flow<Resource<LoginResponse>> {

        return flow{
            emit(Resource.Loading())
            val response = getRetrofitApi().doLogin(basic = Credentials.basic(loginRequest.login, loginRequest.password))
            val data = response.run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(CHECK_YOUR_FIELDS)
                }
            }
            emit(data)
        }.flowOn(mCoroutineContext).catch { error ->
            error.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

}