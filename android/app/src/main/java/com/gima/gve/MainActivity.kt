package com.gima.gve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gima.gve.data.media.MediaThumbnailCache
import com.gima.gve.data.media.RecentMediaStore
import com.gima.gve.data.media.SafMediaInspector
import com.gima.gve.data.repository.LocalFileProjectRepository
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.ui.screens.EditorScreen
import com.gima.gve.ui.screens.HomeScreen
import com.gima.gve.ui.state.EditorStateHolder
import com.gima.gve.ui.state.HomeStateHolder
import com.gima.gve.ui.theme.GveTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GveTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val repository = remember(context) {
                    LocalFileProjectRepository(File(context.filesDir, "gve_projects"))
                }
                val mediaInspector = remember(context) {
                    SafMediaInspector(context.applicationContext)
                }
                val recentMediaStore = remember(context) {
                    RecentMediaStore(File(context.filesDir, "recent_media.json"))
                }
                val thumbnailCache = remember(context) {
                    MediaThumbnailCache(context.applicationContext)
                }
                val homeStateHolder = remember(repository, mediaInspector, recentMediaStore, scope) {
                    HomeStateHolder(
                        repository = repository,
                        mediaInspector = mediaInspector,
                        recentMediaStore = recentMediaStore,
                        scope = scope
                    )
                }

                var activeProject by remember { mutableStateOf<TimelineProject?>(null) }

                val currentProject = activeProject
                if (currentProject == null) {
                    HomeScreen(
                        stateHolder = homeStateHolder,
                        onOpenProject = { project -> activeProject = project }
                    )
                } else {
                    val editorStateHolder = remember(currentProject.projectId) {
                        EditorStateHolder(
                            initialProject = currentProject,
                            scope = scope,
                            repository = repository,
                            mediaInspector = mediaInspector,
                            recentMediaStore = recentMediaStore
                        )
                    }
                    EditorScreen(
                        stateHolder = editorStateHolder,
                        thumbnailCache = thumbnailCache,
                        onExitToHome = {
                            activeProject = null
                            homeStateHolder.refreshRecentProjects()
                        }
                    )
                }
            }
        }
    }
}
