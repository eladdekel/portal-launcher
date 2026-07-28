package com.iblu01.portallauncher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt entry point: hosts the app-wide dependency graph (SingletonComponent). */
@HiltAndroidApp
class PortalApp : Application()
