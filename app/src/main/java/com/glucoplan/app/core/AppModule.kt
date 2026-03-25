package com.glucoplan.app.core

import android.content.Context
import com.glucoplan.app.data.db.AppDatabase
import com.glucoplan.app.data.repository.GlucoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideRepository(db: AppDatabase) = GlucoRepository(
        productDao = db.productDao(),
        panDao = db.panDao(),
        dishDao = db.dishDao(),
        mealDao = db.mealDao(),
        settingsDao = db.settingsDao(),
        injectionDao = db.injectionDao()
    )
}
