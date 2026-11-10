package com.routemate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.routemate.app.ui.RouteMateApp
import com.routemate.app.ui.RouteMateTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: RouteMateViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[RouteMateViewModel::class.java]
        setContent { RouteMateTheme { RouteMateApp(model) } }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.refreshDate() }
}
