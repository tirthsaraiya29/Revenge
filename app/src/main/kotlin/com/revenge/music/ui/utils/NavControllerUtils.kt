package com.revenge.music.ui.utils

import androidx.navigation.NavController
import com.revenge.music.ui.screens.Screens

fun NavController.backToMain() {
  val mainRoutes = Screens.MainScreens.filterNotNull().map { it.route }

  while (
    previousBackStackEntry != null && currentBackStackEntry?.destination?.route !in mainRoutes
  ) {
    popBackStack()
  }
}
