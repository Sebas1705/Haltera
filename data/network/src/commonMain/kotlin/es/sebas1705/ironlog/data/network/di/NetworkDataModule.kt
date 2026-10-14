package es.sebas1705.ironlog.data.network.di

import es.sebas1705.ironlog.data.network.api.UserApi
import org.koin.dsl.module

val networkDataModule = module {
    single { UserApi(client = get(), baseUrl = getProperty("BASE_URL", "https://api.example.com")) }
}
