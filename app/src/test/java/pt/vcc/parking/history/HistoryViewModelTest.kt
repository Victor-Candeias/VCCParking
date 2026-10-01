package pt.vcc.parking.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.parking.data.local.FakeParkedCarDao
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.data.local.HistorySettings
import pt.vcc.parking.data.local.ParkedCarEntity
import pt.vcc.parking.data.local.ParkedPhotoStore
import pt.vcc.parking.data.repository.ParkingHistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an empty history is not confused with one still loading`() = test { viewModel, _ ->
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertTrue(state.isEmpty)
        assertEquals(0, state.totalCount)
    }

    @Test
    fun `the first page is limited and the rest is read on request`() = test { viewModel, fixture ->
        repeat(HistoryViewModel.PAGE_SIZE + 5) { fixture.parkAndEnd(it) }
        advanceUntilIdle()

        assertEquals(HistoryViewModel.PAGE_SIZE, viewModel.uiState.value.entries.size)
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(HistoryViewModel.PAGE_SIZE + 5, viewModel.uiState.value.entries.size)
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun `the active record never shows up in the history`() = test { viewModel, fixture ->
        fixture.parkAndEnd(0)
        fixture.parkActive()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `deleting offers to undo and keeps the photo until then`() = test { viewModel, fixture ->
        val id = fixture.parkAndEnd(0, photoUri = PHOTO_URI)
        advanceUntilIdle()

        viewModel.delete(id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertTrue(viewModel.uiState.value.message is HistoryMessage.Deleted)
        assertTrue(fixture.removedPhotos.isEmpty())

        viewModel.undoDelete()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertNull(viewModel.uiState.value.message)
        assertTrue(fixture.removedPhotos.isEmpty())
    }

    @Test
    fun `closing the message makes the removal final`() = test { viewModel, fixture ->
        val id = fixture.parkAndEnd(0, photoUri = PHOTO_URI)
        advanceUntilIdle()

        viewModel.delete(id)
        advanceUntilIdle()
        viewModel.dismissMessage()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf(PHOTO_URI), fixture.removedPhotos)
    }

    @Test
    fun `clearing the history reports how much was deleted`() = test { viewModel, fixture ->
        repeat(3) { fixture.parkAndEnd(it) }
        advanceUntilIdle()

        viewModel.deleteAll()
        advanceUntilIdle()

        assertEquals(HistoryMessage.Cleared(3), viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `opening the screen applies the retention`() = test(
        prepare = {
            // Terminou ha mais de 90 dias, que e a politica por omissao.
            park(
                parkedAtMillis = NOW_MILLIS - 200 * ONE_DAY_MILLIS,
                endedAtMillis = NOW_MILLIS - 199 * ONE_DAY_MILLIS,
            )
        },
    ) { viewModel, _ ->
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `the exported content covers the whole history and not just the page`() =
        test { viewModel, fixture ->
            repeat(HistoryViewModel.PAGE_SIZE + 2) { fixture.parkAndEnd(it) }
            advanceUntilIdle()

            var exported: String? = null
            viewModel.export(HistoryExportFormat.Csv) { exported = it }
            advanceUntilIdle()

            val lines = exported!!.trim().lines()
            assertEquals(HistoryViewModel.PAGE_SIZE + 2 + 1, lines.size)
            assertEquals(HistoryMessage.Exported, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.exporting)
        }

    @Test
    fun `a failed export is reported instead of silently ignored`() = test { viewModel, fixture ->
        fixture.parkAndEnd(0)
        advanceUntilIdle()

        viewModel.export(HistoryExportFormat.GeoJson) { error("sem espaco") }
        advanceUntilIdle()

        assertEquals(HistoryMessage.ExportFailed, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.exporting)
    }

    @Test
    fun `choosing another retention is kept and applied`() = test { viewModel, fixture ->
        fixture.park(
            parkedAtMillis = NOW_MILLIS - 45 * ONE_DAY_MILLIS,
            endedAtMillis = NOW_MILLIS - 44 * ONE_DAY_MILLIS,
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)

        viewModel.setRetention(HistoryRetention.Days30)
        advanceUntilIdle()

        assertEquals(HistoryRetention.Days30, viewModel.uiState.value.retention)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    private fun test(
        prepare: suspend Fixture.() -> Unit = {},
        body: suspend TestScope.(HistoryViewModel, Fixture) -> Unit,
    ) = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.prepare()
        val viewModel = HistoryViewModel(fixture.repository) { NOW_MILLIS }

        // `stateIn(WhileSubscribed)` so produz estado com alguem a observar.
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        body(viewModel, fixture)
    }

    private class Fixture {
        val dao = FakeParkedCarDao()
        val removedPhotos = mutableListOf<String>()

        val repository = ParkingHistoryRepository(
            dao = dao,
            settings = InMemoryHistorySettings(),
            photos = ParkedPhotoStore { removedPhotos += it },
            now = { NOW_MILLIS },
        )

        suspend fun park(parkedAtMillis: Long, endedAtMillis: Long?, photoUri: String? = null) =
            dao.insert(
                ParkedCarEntity(
                    latitude = LATITUDE,
                    longitude = LONGITUDE,
                    parkedAtMillis = parkedAtMillis,
                    endedAtMillis = endedAtMillis,
                    photoUri = photoUri,
                ),
            )

        suspend fun parkAndEnd(index: Int, photoUri: String? = null): Long {
            val parkedAt = NOW_MILLIS - (index + 1) * ONE_HOUR_MILLIS
            return park(parkedAt, parkedAt + HALF_HOUR_MILLIS, photoUri)
        }

        suspend fun parkActive(): Long = park(NOW_MILLIS, endedAtMillis = null)
    }

    private class InMemoryHistorySettings : HistorySettings {
        private val state = MutableStateFlow(HistoryRetention.Default)
        override val retention = state

        override suspend fun setRetention(retention: HistoryRetention) {
            state.value = retention
        }
    }

    private companion object {
        const val LATITUDE = 38.7336
        const val LONGITUDE = -9.1447
        const val PHOTO_URI = "file:///data/parked/parked-1.jpg"
        const val NOW_MILLIS = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
        const val HALF_HOUR_MILLIS = 1_800_000L
        const val ONE_DAY_MILLIS = 86_400_000L
    }
}
