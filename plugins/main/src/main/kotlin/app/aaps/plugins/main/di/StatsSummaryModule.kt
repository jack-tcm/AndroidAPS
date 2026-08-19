package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.statsSummary.StatsSummaryFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class StatsSummaryModule {

    @ContributesAndroidInjector abstract fun contributesStatsSummaryFragment(): StatsSummaryFragment
}
