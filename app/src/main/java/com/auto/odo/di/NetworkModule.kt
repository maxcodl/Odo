package com.auto.odo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideDriveService(): Drive {
        // Note: You will need to inject the credential here later, 
        // but this provides the structure.
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            null // Credential will be set when the user logs in
        ).setApplicationName("OdoAuto").build()
    }
}