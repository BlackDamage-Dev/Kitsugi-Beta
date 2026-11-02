package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailAutoPagingColumnTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun portraitOuterLazyListLoadsBatchesWithoutClicking() {
        compose.setContent {
            LazyColumn(Modifier.size(320.dp, 240.dp)) {
                item {
                    DetailAutoPagingColumn(items = (0 until 37).toList()) { value ->
                        Box(Modifier.height(60.dp).testTag("row_$value"))
                    }
                }
            }
        }
        compose.onNodeWithTag("row_11").assertExists()
        compose.onNodeWithTag("row_12").assertDoesNotExist()
        compose.onNodeWithTag("row_11").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_23").assertExists()
        compose.onNodeWithTag("row_24").assertDoesNotExist()
        compose.onNodeWithTag("row_23").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_35").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_36").assertExists()
        compose.onNodeWithTag("row_37").assertDoesNotExist()
    }

    @Test
    fun landscapeTwoColumnListLoadsOnScrollAndKeepsOddLastItem() {
        compose.setContent {
            Column(Modifier.size(320.dp, 240.dp).verticalScroll(rememberScrollState())) {
                DetailAutoPagingColumn(items = (0 until 25).toList(), columns = 2) { value ->
                    Box(Modifier.height(60.dp).testTag("row_$value"))
                }
            }
        }
        compose.onNodeWithTag("row_12").assertDoesNotExist()
        compose.onNodeWithTag("row_10").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_23").assertExists()
        compose.onNodeWithTag("row_24").assertDoesNotExist()
        compose.onNodeWithTag("row_22").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_24").assertExists()
    }

    @Test
    fun offscreenPagerPageDoesNotDrainDataAndResumesWhenSelected() {
        val active = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDetailPagingActive provides active.value) {
                Column(Modifier.size(320.dp, 240.dp).verticalScroll(rememberScrollState())) {
                    DetailAutoPagingColumn(items = (0 until 60).toList(), batchSize = 4) { value ->
                        Box(Modifier.height(60.dp).testTag("row_$value"))
                    }
                }
            }
        }
        compose.onNodeWithTag("row_3").assertExists()
        compose.onNodeWithTag("row_4").assertDoesNotExist()
        compose.runOnIdle { active.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("row_4").assertExists()
        compose.onNodeWithTag("row_59").assertDoesNotExist()
    }

    @Test
    fun replacingDataResetsTheBatchAndDoesNotKeepOldCards() {
        val data = mutableStateOf((0 until 60).toList())
        compose.setContent {
            Column(Modifier.size(320.dp, 240.dp).verticalScroll(rememberScrollState())) {
                DetailAutoPagingColumn(items = data.value) { value ->
                    Box(Modifier.height(60.dp).testTag("row_$value"))
                }
            }
        }
        compose.onNodeWithTag("row_11").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("row_0").performScrollTo()
        compose.waitForIdle()
        compose.runOnIdle { data.value = (100 until 160).toList() }
        compose.waitForIdle()
        compose.onNodeWithTag("row_0").assertDoesNotExist()
        compose.onNodeWithTag("row_111").assertExists()
        compose.onNodeWithTag("row_123").assertDoesNotExist()
    }
}
