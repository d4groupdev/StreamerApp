package com.mycompany.data.repository

import com.mycompany.BuildConfig
import com.mycompany.data.Resource
import com.mycompany.data.api.API
import com.mycompany.data.managers.SessionManager
import com.mycompany.data.model.ERROR_CHANGE_STREAM_STATUS
import com.mycompany.data.model.NETWORK_ERROR
import com.mycompany.data.model.User
import com.mycompany.data.model.stream.*
import com.mycompany.data.model.stream.chat_room.CloseChatRoomRequest
import com.mycompany.data.model.stream.chat_room.CloseChatRoomResponse
import com.mycompany.data.model.stream.chat_room.OpenChatRoomResponse
import com.mycompany.data.model.stream.chat_room.OpenRoomRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class StreamRepository @Inject constructor(ioCoroutineContext: CoroutineContext, retrofitApi: API, sessionManager: SessionManager) {

    private val mRetrofitProduct = retrofitApi
    private val mSessionManager = sessionManager
    private val coroutineContext = ioCoroutineContext

    private val mRetrofitDev = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_DEVELOPMENT_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(API::class.java)


    private fun getRetrofitApi(): API{
        return if(mSessionManager.getIsDevMode()){
            mRetrofitDev
        }else{
            mRetrofitProduct
        }
    }

    suspend fun fetchStreamList(appId: String, streamerId: String): Flow<Resource<StreamResponse>> {
        val streamRequest = StreamRequest(Filters(streamerId))
        return flow{
            emit(Resource.Loading())
            val response = getRetrofitApi().getLiveStreamsData(appId, streamRequest)
            val data = response.run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(NETWORK_ERROR)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch { resource ->
            resource.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

    suspend fun fetchStreamData(baseAuth:String, streamId: Int): Flow<Resource<StreamResponse>>{
        return flow{
            emit(Resource.Loading())
            val response = getRetrofitApi().getStreamData(baseAuth, streamId)
            val data = response.run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(NETWORK_ERROR)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch { resource ->
            resource.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

    suspend fun createStreamLink(baseAuth:String, streamId: String): Flow<Resource<StreamLinkResponse>>{
        return flow{
            emit(Resource.Loading())
            val response = getRetrofitApi().createStreamLink(baseAuth, CreateStreamLinkRequest(streamId))
            val data = response.run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(NETWORK_ERROR)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch { resource ->
            resource.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

    suspend fun changeStreamStatus(baseAuth:String, changeStreamStateRequest: ChangeStreamStateRequest): Flow<Resource<ChangeStreamStateResponse>>{
        return flow{
            emit(Resource.Loading())
            val response = getRetrofitApi().changeStreamState(baseAuth, changeStreamStateRequest)
                val data = response.run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(ERROR_CHANGE_STREAM_STATUS)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch { resource ->
            resource.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

    suspend fun openChatRoom(baseAuth: String, openRoomRequest: OpenRoomRequest): Flow<Resource<OpenChatRoomResponse>>{
        return flow{
            emit(Resource.Loading())
            val data = getRetrofitApi().openChatRoom(baseAuth, openRoomRequest).run {
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(NETWORK_ERROR)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch{ error ->
            error.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }

    suspend fun closeChatRoom(baseAuth: String, closeRoomRequest: CloseChatRoomRequest): Flow<Resource<CloseChatRoomResponse>>{
        return flow{
            emit(Resource.Loading())
            val data = getRetrofitApi().closeChatRoom(baseAuth, closeRoomRequest).run{
                if(status == 200){
                    Resource.Success(this)
                }else{
                    Resource.DataError(NETWORK_ERROR)
                }
            }
            emit(data)
        }.flowOn(coroutineContext).catch { error ->
            error.printStackTrace()
            emit(Resource.DataError(NETWORK_ERROR))
        }
    }
}