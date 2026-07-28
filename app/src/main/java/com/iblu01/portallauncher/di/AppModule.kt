package com.iblu01.portallauncher.di

import android.content.Context
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.SettingsChangeBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-wide bindings. `PillRepository` is `@Inject`-constructed + `@Singleton` (annotated in place),
 * so only the framework-bound `Prefs(Context)` needs an explicit provider here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePrefs(@ApplicationContext context: Context): Prefs = Prefs(context)

    @Provides
    @Singleton
    fun provideSettingsChangeBus(): SettingsChangeBus = SettingsChangeBus.get()
}
