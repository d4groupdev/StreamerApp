package com.mycompany.di

import android.content.Context
import android.content.SharedPreferences
import com.mycompany.data.managers.SessionManager
import com.mycompany.data.model.User
import com.mycompany.data.managers.ErrorManager
import com.mycompany.ui.adapter.StreamListRVAdapter
import com.mycompany.utils.Network
import com.mycompany.utils.NetworkConnectivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideNetworkConnectivity(@ApplicationContext context: Context): NetworkConnectivity {
        return Network(context)
    }

    @Provides
    @Singleton
    fun provideCoroutineContext(): CoroutineContext {
        return Dispatchers.IO
    }

    @Provides
    @Singleton
    fun provideTimerScope(): CoroutineScope{
        return CoroutineScope(Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideErrorManager(@ApplicationContext context: Context): ErrorManager {
        return ErrorManager(context)
    }

    @Provides
    @Singleton
    fun provideUser(): User {
        return User()
    }

    @Singleton
    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context) =
        context.getSharedPreferences("USER", Context.MODE_PRIVATE)

    @Singleton
    @Provides
    fun provideSessionManager(preferences: SharedPreferences) = SessionManager(preferences)


}