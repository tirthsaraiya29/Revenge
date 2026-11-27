package com.revenge.music.ui.screens.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.revenge.core.constants.PauseSearchHistoryKey
import com.revenge.core.constants.SearchSource
import com.revenge.core.constants.SearchSourceKey
import com.revenge.core.db.entities.SearchHistory
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.utils.YouTubeUrlParser
import com.revenge.music.LocalDatabase
import com.revenge.music.LocalIsPlayerExpanded
import com.revenge.music.LocalPlayerAwareWindowInsets
import com.revenge.music.LocalPlayerConnection
import com.revenge.music.R
import com.revenge.music.ui.theme.RevengeColor
import com.revenge.music.ui.theme.RevengeRadius
import com.revenge.music.ui.theme.RevengeSpacing
import com.revenge.music.utils.rememberEnumPreference
import com.revenge.music.utils.rememberPreference
import com.revenge.music.viewmodels.HomeViewModel
import com.revenge.music.viewmodels.MoodAndGenresViewModel
import com.revenge.playback.queues.YouTubeQueue
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ExploreGenreCategory(
  val title: String,
  val searchQuery: String,
  val startColor: Color,
  val endColor: Color,
  val imageUrl: String,
)

private val EXPLORE_GENRE_CATEGORIES =
  listOf(
    ExploreGenreCategory(
      title = "Live & Acoustic",
      searchQuery = "Live Concert Music",
      startColor = Color(0xFF1E222A),
      endColor = Color(0xFF121418),
      imageUrl =
        "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Top Hits",
      searchQuery = "Top Hits",
      startColor = Color(0xFF8B5A2B),
      endColor = Color(0xFF4A2E14),
      imageUrl =
        "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Alternative",
      searchQuery = "Alternative Rock",
      startColor = Color(0xFF3B5249),
      endColor = Color(0xFF1E2D27),
      imageUrl =
        "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Pop",
      searchQuery = "Pop Hits",
      startColor = Color(0xFF6B3A5B),
      endColor = Color(0xFF3B1E32),
      imageUrl =
        "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Hip-Hop",
      searchQuery = "Hip Hop",
      startColor = Color(0xFF2B3A67),
      endColor = Color(0xFF151D36),
      imageUrl =
        "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Country",
      searchQuery = "Country Music",
      startColor = Color(0xFF6B4423),
      endColor = Color(0xFF382210),
      imageUrl =
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Electronic",
      searchQuery = "Dance Electronic",
      startColor = Color(0xFF1E4E5F),
      endColor = Color(0xFF0F2B35),
      imageUrl =
        "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Rock",
      searchQuery = "Classic Rock",
      startColor = Color(0xFF5A2A27),
      endColor = Color(0xFF331513),
      imageUrl =
        "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Latin",
      searchQuery = "Latin Music",
      startColor = Color(0xFF702D3B),
      endColor = Color(0xFF3D161F),
      imageUrl =
        "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "R&B & Soul",
      searchQuery = "R&B Soul",
      startColor = Color(0xFF4A3560),
      endColor = Color(0xFF261933),
      imageUrl =
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Indie",
      searchQuery = "Indie Pop Folk",
      startColor = Color(0xFF264653),
      endColor = Color(0xFF14242B),
      imageUrl =
        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Workout",
      searchQuery = "Workout Motivation Music",
      startColor = Color(0xFF6E3624),
      endColor = Color(0xFF38180E),
      imageUrl =
        "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Chill",
      searchQuery = "Chill Vibes",
      startColor = Color(0xFF2E3856),
      endColor = Color(0xFF161C2C),
      imageUrl =
        "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Party",
      searchQuery = "Party Hits",
      startColor = Color(0xFF4C2A58),
      endColor = Color(0xFF27142E),
      imageUrl =
        "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Focus & Study",
      searchQuery = "Deep Focus Study",
      startColor = Color(0xFF1D4A44),
      endColor = Color(0xFF0C2421),
      imageUrl =
        "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Classical",
      searchQuery = "Classical Essentials",
      startColor = Color(0xFF4A4231),
      endColor = Color(0xFF242017),
      imageUrl =
        "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Jazz",
      searchQuery = "Jazz Classics",
      startColor = Color(0xFF273248),
      endColor = Color(0xFF131A26),
      imageUrl =
        "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "K-Pop",
      searchQuery = "K-Pop Hits",
      startColor = Color(0xFF5D2E58),
      endColor = Color(0xFF31162E),
      imageUrl =
        "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Metal",
      searchQuery = "Heavy Metal",
      startColor = Color(0xFF381D1D),
      endColor = Color(0xFF1C0D0D),
      imageUrl =
        "https://images.unsplash.com/photo-1526478806334-5fd488fcaabc?w=450&auto=format&fit=crop&q=80",
    ),
    ExploreGenreCategory(
      title = "Ambient & Sleep",
      searchQuery = "Sleep Relaxing Music",
      startColor = Color(0xFF202634),
      endColor = Color(0xFF10131A),
      imageUrl =
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=450&auto=format&fit=crop&q=80",
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  navController: NavController,
  pureBlack: Boolean,
  homeViewModel: HomeViewModel = hiltViewModel(),
  moodAndGenresViewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
  val database = LocalDatabase.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val isPlayerExpanded = LocalIsPlayerExpanded.current
  val playerConnection = LocalPlayerConnection.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  val moodAndGenresList by moodAndGenresViewModel.moodAndGenres.collectAsState()

  var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
  var query by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

  var searchActive by rememberSaveable { mutableStateOf(false) }
  var showSearchContent by remember { mutableStateOf(false) }

  val speechRecognizerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val spokenText =
          result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
          query = TextFieldValue(spokenText)
          if (spokenText.isNotEmpty()) {
            focusManager.clearFocus()
            navController.navigate("search/${URLEncoder.encode(spokenText, "UTF-8")}")
            if (!pauseSearchHistory) {
              coroutineScope.launch(Dispatchers.IO) {
                database.query { insert(SearchHistory(query = spokenText)) }
              }
            }
          }
          searchActive = false
        }
      }
    }

  LaunchedEffect(searchActive) {
    if (searchActive) {
      delay(80)
      showSearchContent = true
    } else {
      showSearchContent = false
    }
  }

  BackHandler(enabled = searchActive) {
    searchActive = false
    query = TextFieldValue("")
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  val onSearch: (String) -> Unit = remember {
    { searchQuery ->
      if (searchQuery.isNotEmpty()) {
        searchActive = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
          is YouTubeUrlParser.Video -> {
            playerConnection?.playQueue(
              YouTubeQueue(
                WatchEndpoint(videoId = parsedUrl.id),
              ),
            )
          }
          is YouTubeUrlParser.Artist -> {
            navController.navigate("artist/${parsedUrl.id}")
          }
          null -> {
            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
          }
        }

        if (!pauseSearchHistory) {
          coroutineScope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = searchQuery)) }
          }
        }
      }
    }
  }

  val onCategoryClick: (ExploreGenreCategory) -> Unit = { category ->
    val matchingItem =
      moodAndGenresList
        ?.flatMap { it.items }
        ?.firstOrNull { item ->
          item.title.equals(category.title, ignoreCase = true) ||
            item.title.contains(category.title, ignoreCase = true) ||
            category.title.contains(item.title, ignoreCase = true)
        }

    if (matchingItem != null) {
      navController.navigate(
        "youtube_browse/${matchingItem.endpoint.browseId}?params=${matchingItem.endpoint.params}",
      )
    } else {
      navController.navigate("search/${URLEncoder.encode(category.searchQuery, "UTF-8")}")
    }
  }

  val bottomPadding =
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

  Scaffold(
    topBar = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
      ) {
        // Top Header: Title "Search"
        if (!searchActive) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.search),
              style =
                MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 24.sp,
                ),
            )
          }
        }

        // Material You Stadium-Pill SearchBar
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(top = if (searchActive) 10.dp else 0.dp, bottom = 12.dp)
              .height(56.dp)
              .clip(CircleShape)
              .background(
                if (pureBlack) Color.White.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              )
              .clickable {
                if (!searchActive) {
                  searchActive = true
                  focusRequester.requestFocus()
                }
              }
              .padding(horizontal = 12.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            IconButton(
              onClick = {
                if (searchActive) {
                  searchActive = false
                  query = TextFieldValue("")
                  focusManager.clearFocus()
                  keyboardController?.hide()
                } else {
                  searchActive = true
                  focusRequester.requestFocus()
                }
              },
              modifier = Modifier.size(36.dp),
            ) {
              Icon(
                painter =
                  painterResource(
                    if (searchActive) R.drawable.arrow_back else R.drawable.search,
                  ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(modifier = Modifier.weight(1f)) {
              if (query.text.isEmpty()) {
                Text(
                  text =
                    if (searchSource == SearchSource.ONLINE) "Artists, Songs, Lyrics & More"
                    else stringResource(R.string.search_library),
                  style =
                    TextStyle(
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                      fontSize = 16.sp,
                    ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                  Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                    if (it.isFocused) searchActive = true
                  },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                  KeyboardActions(
                    onSearch = {
                      focusManager.clearFocus(force = true)
                      keyboardController?.hide()
                      searchActive = false
                      onSearch(query.text)
                    },
                  ),
                textStyle =
                  TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              )
            }

            // Right-side actions: Clear / Mic / Source Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (query.text.isNotEmpty()) {
                IconButton(
                  onClick = { query = TextFieldValue("") },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    try {
                      val intent =
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                          putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                          )
                          putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                        }
                      speechRecognizerLauncher.launch(intent)
                    } catch (e: Exception) {
                      navController.navigate("recognition")
                    }
                  },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                  )
                }
              }

              IconButton(
                onClick = {
                  searchSource =
                    if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL
                    else SearchSource.ONLINE
                },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(
                  painter =
                    painterResource(
                      when (searchSource) {
                        SearchSource.LOCAL -> R.drawable.library_music
                        SearchSource.ONLINE -> R.drawable.globe_search
                      },
                    ),
                  contentDescription = "Search Source",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(21.dp),
                )
              }
            }
          }
        }
      }
    },
    containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Box(
      modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
    ) {
      if (searchActive && showSearchContent) {
        when (searchSource) {
          SearchSource.LOCAL ->
            LocalSearchScreen(
              query = query.text,
              navController = navController,
              onDismiss = {
                searchActive = false
                focusManager.clearFocus()
                keyboardController?.hide()
              },
              pureBlack = pureBlack,
            )
          SearchSource.ONLINE ->
            OnlineSearchScreen(
              query = query.text,
              onQueryChange = { query = it },
              navController = navController,
              onSearch = {
                searchActive = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onSearch(it)
              },
              onDismiss = {
                searchActive = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
              },
              pureBlack = pureBlack,
            )
        }
      } else if (!searchActive) {
        // Explore Categories Grid (2 Columns)
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          contentPadding =
            PaddingValues(
              start = RevengeSpacing.md,
              end = RevengeSpacing.md,
              top = RevengeSpacing.xs,
              bottom = bottomPadding + RevengeSpacing.xl,
            ),
          horizontalArrangement = Arrangement.spacedBy(RevengeSpacing.sm),
          verticalArrangement = Arrangement.spacedBy(RevengeSpacing.sm),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(items = EXPLORE_GENRE_CATEGORIES, key = { it.title }) { category ->
            ExploreGenreCard(
              category = category,
              onClick = { onCategoryClick(category) },
            )
          }
        }
      }
    }
  }

  DisposableEffect(lifecycleOwner, isPlayerExpanded) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
          }
        }
        Lifecycle.Event.ON_PAUSE -> {
          focusManager.clearFocus()
          keyboardController?.hide()
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    if (isPlayerExpanded) {
      keyboardController?.hide()
      focusManager.clearFocus()
    }

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

@Composable
fun ExploreGenreCard(
  category: ExploreGenreCategory,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(96.dp)
        .clip(RoundedCornerShape(RevengeRadius.md))
        .border(
          width = 1.dp,
          color = RevengeColor.BorderSubtleDark,
          shape = RoundedCornerShape(RevengeRadius.md),
        )
        .background(
          Brush.horizontalGradient(
            colors = listOf(category.startColor, category.endColor),
          ),
        )
        .clickable(onClick = onClick),
  ) {
    // Artist / Genre photo spanning the full card with a smooth gradient fade mask
    AsyncImage(
      model = category.imageUrl,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = Alignment.CenterEnd,
      modifier = Modifier.fillMaxSize(),
    )

    // Smooth multi-stop horizontal gradient fade mask
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              colorStops =
                arrayOf(
                  0.0f to category.startColor,
                  0.30f to category.startColor,
                  0.55f to category.startColor.copy(alpha = 0.82f),
                  0.75f to category.startColor.copy(alpha = 0.35f),
                  1.0f to Color.Transparent,
                ),
            ),
          ),
    )

    // Subtle bottom vertical scrim for crisp text readability
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  Color.Transparent,
                  Color.Black.copy(alpha = 0.45f),
                ),
              startY = 40f,
            ),
          ),
    )

    // Category title at bottom left
    Text(
      text = category.title,
      color = Color.White,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 19.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(start = RevengeSpacing.sm, bottom = RevengeSpacing.sm, end = RevengeSpacing.md),
    )
  }
}
