package com.mtgscanner.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.collection.BrowseViewModel
import com.mtgscanner.ui.collection.CollectionViewModel
import com.mtgscanner.ui.detail.CardDetailViewModel
import com.mtgscanner.ui.home.HomeViewModel
import com.mtgscanner.ui.scan.ScanViewModel
import com.mtgscanner.ui.search.SearchViewModel

object AppViewModels {
    private val AppKey = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY

    val Factory = viewModelFactory {
        initializer { HomeViewModel((this[AppKey] as MtgApp).repository) }
        initializer { ScanViewModel((this[AppKey] as MtgApp).repository) }
        initializer { CollectionViewModel((this[AppKey] as MtgApp).repository) }
        initializer { SearchViewModel((this[AppKey] as MtgApp).repository) }
    }

    fun browse(boxId: Long) = viewModelFactory {
        initializer { BrowseViewModel((this[AppKey] as MtgApp).repository, boxId) }
    }

    fun detail(id: Long) = viewModelFactory {
        initializer { CardDetailViewModel((this[AppKey] as MtgApp).repository, id) }
    }
}
