package com.mtgscanner.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.collection.CollectionViewModel
import com.mtgscanner.ui.detail.CardDetailViewModel
import com.mtgscanner.ui.scan.ScanViewModel
import com.mtgscanner.ui.search.SearchViewModel

object AppViewModels {
    private val CreationExtrasApp = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY

    val Factory = viewModelFactory {
        initializer { ScanViewModel((this[CreationExtrasApp] as MtgApp).repository) }
        initializer { CollectionViewModel((this[CreationExtrasApp] as MtgApp).repository) }
        initializer { SearchViewModel((this[CreationExtrasApp] as MtgApp).repository) }
    }

    fun detail(id: Long) = viewModelFactory {
        initializer { CardDetailViewModel((this[CreationExtrasApp] as MtgApp).repository, id) }
    }
}
