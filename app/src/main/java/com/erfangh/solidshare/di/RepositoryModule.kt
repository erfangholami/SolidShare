package com.erfangh.solidshare.di

import com.erfangh.solidshare.data.repo.AuthRepository
import com.erfangh.solidshare.data.repo.AuthRepositoryImp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    fun provideAuthRepository(): AuthRepository {
        return AuthRepositoryImp()
    }

}