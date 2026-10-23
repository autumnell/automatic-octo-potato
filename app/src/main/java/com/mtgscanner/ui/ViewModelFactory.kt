package com.mtgscanner.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.collection.BrowseViewModel
import com.mtgscanner.ui.collection.CollectionViewModel
import com.mtgscanner.ui.decks.DeckViewModel
import com.mtgscanner.ui.decks.DecksViewModel
import com.mtgscanner.ui.settings.SettingsViewModel
import com.mtgscanner.ui.trade.TradeViewModel
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
        initializer { com.mtgscanner.ui.goals.GoalsViewModel((this[AppKey] as MtgApp).repository) }
        initializer { SearchViewModel((this[AppKey] as MtgApp).repository) }
        initializer { DecksViewModel((this[AppKey] as MtgApp).repository) }
        initializer { TradeViewModel((this[AppKey] as MtgApp).repository) }
        initializer { SettingsViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.game.GameViewModel(this[AppKey] as MtgApp, (this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.collection.ToolsViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.home.StatsViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.sell.SellViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.sell.BuylistViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.loans.LoansViewModel((this[AppKey] as MtgApp).repository) }
        initializer { com.mtgscanner.ui.home.MoneyViewModel((this[AppKey] as MtgApp).repository) }
    }

    fun browse(boxId: Long) = viewModelFactory {
        initializer { BrowseViewModel((this[AppKey] as MtgApp).repository, boxId) }
    }

    fun deck(id: Long) = viewModelFactory {
        initializer { DeckViewModel((this[AppKey] as MtgApp).repository, id) }
    }

    fun detail(id: Long) = viewModelFactory {
        initializer { CardDetailViewModel((this[AppKey] as MtgApp).repository, id) }
    }
}
