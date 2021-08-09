package com.mycompany.ui.viewmodel

import android.util.Log
import androidx.lifecycle.*
import com.mycompany.data.Resource
import com.mycompany.data.model.*
import com.mycompany.data.model.socket.SocketIOData
import com.mycompany.data.model.socket.SocketIOState
import com.mycompany.data.model.states.PrepareVideoStreamState
import com.mycompany.data.model.stream.*
import com.mycompany.data.model.stream.chat_room.CloseChatRoomRequest
import com.mycompany.data.model.stream.chat_room.OpenRoomRequest
import com.mycompany.data.repository.SocketRepository
import com.mycompany.data.repository.StreamRepository
import com.mycompany.data.managers.ErrorManager
import com.mycompany.utils.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class VideoStreamViewModel @Inject constructor(streamListRepository: StreamRepository, user: User) :
    ViewModel() {

    var isPaused: Boolean = false
    var isFrontCamera: Boolean = true
    private val mStreamRepository = streamListRepository
    private val mUser = user

    @Inject
    lateinit var errorManager: ErrorManager

    @Inject
    lateinit var socketRepository: SocketRepository

    private var selectedStreamId: String = ""
    private var selectedStreamLinks: TranslationLinks? = null
    private var selectedStreamProducts: MutableList<Product> = mutableListOf()

    private val _prepareStateData = MutableLiveData<Event<PrepareVideoStreamState>>()
    val prepareStateData: LiveData<Event<PrepareVideoStreamState>> = _prepareStateData

    private val _messagesData = MutableLiveData<MutableList<MessageModel>>()
    val messagesData: LiveData<MutableList<MessageModel>> = _messagesData

    private val _viewerData = MutableLiveData<Int>()
    val viewerData: LiveData<Int> = _viewerData

    private val _streamLinksData = MutableLiveData<TranslationLinks?>()
    val streamLinksData: LiveData<TranslationLinks?> = _streamLinksData

    private val _streamProductsData = MutableLiveData<MutableList<Product>>()
    val streamProductsData: LiveData<MutableList<Product>> = _streamProductsData

    private val _videoStreamState = MutableLiveData<Event<VideoStreamState>>()
    val videoStreamState: LiveData<Event<VideoStreamState>> = _videoStreamState

    private var messageList = mutableListOf<MessageModel>()

    private var isStreamEnd = true
    private var isInternetAvailable = true
    private var previousTime = ""


    fun fetchStreamLinks() {
        selectedStreamLinks?.let {
            _streamLinksData.postValue(it)
        } ?: _videoStreamState.postValue(
            Event(
                VideoStreamState(
                    isError = true, errorDescription = errorManager.getError(
                        ERROR_CREATE_LINK
                    ).description
                )
            )
        )
    }

    fun fetchProducts() {
        _streamProductsData.postValue(selectedStreamProducts)
    }

    @InternalCoroutinesApi
    fun fetchStreamData(streamId: String) {
        selectedStreamId = streamId
        viewModelScope.launch {
            if (mUser.profileBasic.isEmpty()) {
                _prepareStateData.postValue(
                    Event(
                        PrepareVideoStreamState(
                            isError = true,
                            errorMessage = errorManager.getError(DEFAULT_ERROR).description
                        )
                    )
                )
                return@launch
            }
            val response =
                mStreamRepository.fetchStreamData(mUser.profileBasic, selectedStreamId.toInt())
            response.collect(collector = object : FlowCollector<Resource<StreamResponse>> {
                override suspend fun emit(value: Resource<StreamResponse>) {
                    when (value) {
                        is Resource.DataError -> {
                            _prepareStateData.postValue(
                                Event(
                                    PrepareVideoStreamState(
                                        isError = true,
                                        errorMessage = errorManager.getError(DEFAULT_ERROR).description
                                    )
                                )
                            )
                        }
                        is Resource.Loading -> {
                            _prepareStateData.postValue(
                                Event(
                                    PrepareVideoStreamState(
                                        isLoading = true
                                    )
                                )
                            )
                        }
                        is Resource.Success -> {
                            selectedStreamLinks = value.data?.body?.translationLinks
                            selectedStreamProducts =
                                value.data?.body?.products?.toMutableList() ?: mutableListOf()

                            if (selectedStreamLinks == null) {
                                fetchStreamLink()
                            } else {
                                changeStreamStatus(StreamStatus.ONLINE)
                            }

                        }
                    }
                }

            })
        }
    }

    @InternalCoroutinesApi
    private suspend fun fetchStreamLink() {
        val response = mStreamRepository.createStreamLink(mUser.profileBasic, selectedStreamId)
        response.collect { it ->
            when (it) {
                is Resource.DataError -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isError = true,
                                errorMessage = errorManager.getError(ERROR_CREATE_LINK).description
                            )
                        )
                    )
                }
                is Resource.Loading -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isLoading = true
                            )
                        )
                    )
                }
                is Resource.Success -> {
                    selectedStreamLinks = it.data?.body
                    it.data?.let {
                        if (it.body.push.rtmp.isNullOrEmpty() && it.body.push.rtmps.isNullOrEmpty()) {
                            _prepareStateData.postValue(
                                Event(
                                    PrepareVideoStreamState(
                                        isError = true, errorMessage = errorManager.getError(
                                            ERROR_CREATE_LINK
                                        ).description
                                    )
                                )
                            )
                        } else {
                            changeStreamStatus(StreamStatus.ONLINE)
                        }
                    }
                }
            }
        }
    }

    private suspend fun changeStreamStatus(status: StreamStatus) {
        if (selectedStreamId.isEmpty()) {
            _prepareStateData.postValue(
                Event(
                    PrepareVideoStreamState(
                        isError = true,
                        errorMessage = errorManager.getError(DEFAULT_ERROR).description
                    )
                )
            )

        }
        val response = mStreamRepository.changeStreamStatus(
            mUser.profileBasic,
            ChangeStreamStateRequest(selectedStreamId, status.status)
        )
        response.collect {
            when (it) {
                is Resource.Loading -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isLoading = true
                            )
                        )
                    )
                }
                is Resource.DataError -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isError = true,
                                errorMessage = errorManager.getError(DEFAULT_ERROR).description
                            )
                        )
                    )

                }
                is Resource.Success -> {
                    when (status) {
                        StreamStatus.COMPLETED -> {
                            _videoStreamState.postValue(Event(VideoStreamState(isEndStream = true)))
                        }
                        StreamStatus.ONLINE -> {
                            isStreamEnd = false
                            openChatRoom()
                        }
                        StreamStatus.PLANNED -> {
                        }
                    }
                }
            }
        }
    }

    private fun prepareSocketIo() {
        socketRepository.stateData.observeForever { socketIoState ->
            when (socketIoState) {
                is SocketIOState.Connect -> {
                    socketRepository.auth(mUser.profileId, mUser.password)
                    viewModelScope.launch {
                        _prepareStateData.postValue(Event(PrepareVideoStreamState(isStopPreview = true)))
                        delay(1000)
                        _prepareStateData.postValue(Event(PrepareVideoStreamState()))
                    }

                }
                is SocketIOState.Disconnect -> {
                    if (!isStreamEnd && isInternetAvailable) {
                        socketRepository.connectToChatServer(selectedStreamId)
                    }
                }
                is SocketIOState.Loading -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isLoading = true
                            )
                        )
                    )
                }
                is SocketIOState.Error -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isError = true
                            )
                        )
                    )
                }
                is SocketIOState.Massage -> {
                    when (val data = parseWebSocketMessage(socketIoState.message ?: "")) {
                        is SocketIOData.Empty -> {

                        }
                        is SocketIOData.NewViewer -> {
                            _viewerData.postValue(data.onlineViewers)
                        }
                        is SocketIOData.NewMessages -> {
                            messageList.addAll(data.socketMessages)
                            _messagesData.postValue(messageList)
                        }
                    }
                }
            }
        }

        if (isInternetAvailable) {
            socketRepository.connectToChatServer(selectedStreamId)
        }

    }

    private suspend fun openChatRoom() {
        val response = mStreamRepository.openChatRoom(
            mUser.profileBasic,
            OpenRoomRequest(mUser.profileId, selectedStreamId, mUser.profileApplication)
        )
        response.collect {
            when (it) {
                is Resource.Loading -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isLoading = true
                            )
                        )
                    )
                }
                is Resource.DataError -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isError = true,
                                errorMessage = errorManager.getError(DEFAULT_ERROR).description
                            )
                        )
                    )
                }
                is Resource.Success -> {
                    prepareSocketIo()

                }
            }
        }
    }

    private suspend fun closeChatRoom() {
        val response = mStreamRepository.closeChatRoom(
            mUser.profileBasic,
            CloseChatRoomRequest(mUser.profileId, selectedStreamId, mUser.profileApplication)
        )
        response.collect {
            when (it) {
                is Resource.Loading -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isLoading = true
                            )
                        )
                    )
                }
                is Resource.DataError -> {
                    _prepareStateData.postValue(
                        Event(
                            PrepareVideoStreamState(
                                isError = true,
                                errorMessage = errorManager.getError(DEFAULT_ERROR).description
                            )
                        )
                    )
                }
                is Resource.Success -> {

                }
            }
        }
    }

    fun finishStream() {
        if (isStreamEnd) {
            return
        }
        viewModelScope.launch {
            socketRepository.disconnect()
            _videoStreamState.postValue(Event(VideoStreamState(isLoading = true)))
            closeChatRoom()
            changeStreamStatus(StreamStatus.COMPLETED)
            selectedStreamLinks = null
            _streamLinksData.postValue(selectedStreamLinks)
            selectedStreamId = ""
            delay(6000)
            _videoStreamState.postValue(Event(VideoStreamState(isEndStream = true)))
            messageList = mutableListOf<MessageModel>()
            _messagesData.postValue(messageList)
        }
        isStreamEnd = true
    }

    fun onErrorRtmp() {
        socketRepository.disconnect()
        if (isInternetAvailable) {
            _videoStreamState.postValue(
                Event(
                    VideoStreamState(
                        isError = true, errorDescription = errorManager.getError(
                            DEFAULT_ERROR
                        ).description
                    )
                )
            )
            finishStream()
        }

    }

    fun onItemSelected(position: Int) {
        socketRepository.highlightProduct(selectedStreamProducts[position].id.toString())
    }

    fun onSuccessRtmp() {
        isStreamEnd = false
        _videoStreamState.postValue(Event(VideoStreamState()))
    }

    fun onInternetIsOn(isEnable: Boolean) {
        if (!isEnable && !isStreamEnd) {
            socketRepository.disconnect()
        }
        if (!isInternetAvailable && isEnable && !isStreamEnd) {
            socketRepository.connectToChatServer(selectedStreamId)
        }
        isInternetAvailable = isEnable
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            socketRepository.disconnect()
            closeChatRoom()
            changeStreamStatus(StreamStatus.COMPLETED)
        }
        selectedStreamLinks = null
    }

    fun onErrorPrepareStream() {
        _prepareStateData.postValue(
            Event(
                PrepareVideoStreamState(
                    isError = true,
                    errorMessage = "Your device does not support streaming"
                )
            )
        )
    }

    fun clearVideoStreamState() {
        _videoStreamState.postValue((Event(VideoStreamState())))
    }

}