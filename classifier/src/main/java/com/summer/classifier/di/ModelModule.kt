package com.summer.classifier.di

import android.content.Context
import com.summer.classifier.SmsClassifier
import com.summer.classifier.ml.model.SmsClassifierModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {

    @Provides
    @Singleton
    fun provideSmsClassifier(@ApplicationContext context: Context): SmsClassifier {
        return SmsClassifierModel(context)
    }
}
