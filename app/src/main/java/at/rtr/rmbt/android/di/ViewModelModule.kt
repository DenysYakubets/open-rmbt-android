package at.rtr.rmbt.android.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.rtr.rmbt.android.viewmodel.HistoryViewModel
import at.rtr.rmbt.android.viewmodel.HomeViewModel
import at.rtr.rmbt.android.viewmodel.MapViewModel
import at.rtr.rmbt.android.viewmodel.MeasurementViewModel
import at.rtr.rmbt.android.viewmodel.NetworkDetailsViewModel
import at.rtr.rmbt.android.viewmodel.SettingsViewModel
import at.rtr.rmbt.android.viewmodel.StatisticsViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/**
 * Class for mapping view models
 * Each method should have an unique name
 */
@Module
interface ViewModelModule {

    @Binds
    fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(HomeViewModel::class)
    fun bindHomeViewModel(viewModel: HomeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(HistoryViewModel::class)
    fun bindHistoryViewModel(viewModel: HistoryViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MapViewModel::class)
    fun bindMapViewModel(viewModel: MapViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(StatisticsViewModel::class)
    fun bindStatisticsViewModel(viewModel: StatisticsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(NetworkDetailsViewModel::class)
    fun bindNetworkDetailsViewModel(viewModel: NetworkDetailsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SettingsViewModel::class)
    fun bindSettingsViewModel(viewModel: SettingsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MeasurementViewModel::class)
    fun bindMeasurementViewModel(viewModel: MeasurementViewModel): ViewModel
}