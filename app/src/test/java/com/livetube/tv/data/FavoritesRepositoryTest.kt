package com.livetube.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRepositoryTest {
    @Test
    fun addsRemovesAndPersistsFavoriteIdsAcrossRepositoryInstances() = runBlocking {
        val backend = PersistentBackend()
        val firstRepository = FavoritesRepository(FakeFavoriteStorage(backend))
        firstRepository.add("aaj_tak")
        firstRepository.add("abp_news")
        assertEquals(setOf("aaj_tak", "abp_news"), firstRepository.favoriteChannelIds.first())

        val reopenedRepository = FavoritesRepository(FakeFavoriteStorage(backend))
        assertEquals(setOf("aaj_tak", "abp_news"), reopenedRepository.favoriteChannelIds.first())
        reopenedRepository.remove("aaj_tak")
        reopenedRepository.setFavorite("sanskar_tv", true)
        assertEquals(
            setOf("abp_news", "sanskar_tv"),
            FavoritesRepository(FakeFavoriteStorage(backend)).favoriteChannelIds.first(),
        )
        reopenedRepository.setFavorite("sanskar_tv", false)
        assertFalse("sanskar_tv" in reopenedRepository.favoriteChannelIds.first())
    }

    @Test
    fun rejectsInvalidChannelIds() = runBlocking {
        val repository = FavoritesRepository(FakeFavoriteStorage(PersistentBackend()))
        assertTrue(runCatching { repository.add("Not Valid") }.isFailure)
    }

    private class FakeFavoriteStorage(
        private val backend: PersistentBackend,
    ) : FavoriteStorage {
        override val favoriteChannelIds: Flow<Set<String>> = backend.ids
        override suspend fun update(transform: (Set<String>) -> Set<String>) {
            backend.ids.value = transform(backend.ids.value)
        }
    }

    private class PersistentBackend {
        val ids = MutableStateFlow<Set<String>>(emptySet())
    }
}
