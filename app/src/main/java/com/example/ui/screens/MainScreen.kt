@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Language
import com.example.ui.viewmodel.BartaViewModel
import com.example.ui.viewmodel.SubScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: BartaViewModel) {
    val currentSubScreen by viewModel.currentSubScreen.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val strings = remember(currentLanguage) { AppStrings(currentLanguage) }

    // Overlays
    val viewingUserProfile by viewModel.viewingUserProfile.collectAsState()
    val viewingStoryUser by viewModel.viewingStoryUser.collectAsState()
    val activeCommentPostId by viewModel.activeCommentPostId.collectAsState()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isNotificationsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MainTopAppBar(
                viewModel = viewModel,
                strings = strings,
                onSettingsClick = { isSettingsOpen = true },
                onNotificationsClick = { isNotificationsOpen = true }
            )
        },
        bottomBar = {
            MainBottomBar(
                currentSubScreen = currentSubScreen,
                strings = strings,
                onTabSelected = { viewModel.navigateToSubScreen(it) }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "SubScreenNavigation"
            ) { subScreen ->
                when (subScreen) {
                    SubScreen.Home -> HomeScreen(viewModel, strings)
                    SubScreen.Explore -> ExploreScreen(viewModel, strings)
                    SubScreen.CreatePost -> CreatePostScreen(viewModel, strings)
                    SubScreen.Notifications -> NotificationsScreen(viewModel, strings)
                    SubScreen.Reels -> ReelsScreen(viewModel, strings)
                    SubScreen.Profile -> ProfileScreen(viewModel, viewModel.currentUser.value, strings, isOwnProfile = true)
                }
            }

            // Overlay: Settings Modal
            if (isSettingsOpen) {
                SettingsDialog(
                    viewModel = viewModel,
                    strings = strings,
                    onDismiss = { isSettingsOpen = false }
                )
            }

            // Overlay: Notifications Modal
            if (isNotificationsOpen) {
                NotificationsDialog(
                    viewModel = viewModel,
                    strings = strings,
                    onDismiss = { isNotificationsOpen = false }
                )
            }

            // Overlay: Other User Profile Screen
            viewingUserProfile?.let { targetUser ->
                Dialog(
                    onDismissRequest = { viewModel.clearViewingUserProfile() },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("@${targetUser.username}", fontWeight = FontWeight.Bold) },
                                navigationIcon = {
                                    IconButton(onClick = { viewModel.clearViewingUserProfile() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            )
                        }
                    ) { pad ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(pad)
                        ) {
                            ProfileScreen(
                                viewModel = viewModel,
                                user = targetUser,
                                strings = strings,
                                isOwnProfile = false
                            )
                        }
                    }
                }
            }

            // Overlay: Story Viewer
            viewingStoryUser?.let { targetUsername ->
                StoryViewerOverlay(
                    viewModel = viewModel,
                    username = targetUsername,
                    onDismiss = { viewModel.viewStoryForUser(null) }
                )
            }

            // Overlay: Comments Bottom Sheet Simulation
            activeCommentPostId?.let { postId ->
                CommentsOverlay(
                    viewModel = viewModel,
                    postId = postId,
                    strings = strings,
                    onDismiss = { viewModel.openCommentsForPost(null) }
                )
            }
        }
    }
}

// --- Dynamic Media Loader Component ---
@Composable
fun BartaMediaLoader(
    mediaPath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var bitmapState by remember(mediaPath) { mutableStateOf<Bitmap?>(null) }

    // Resolve static assets vs base64
    LaunchedEffect(mediaPath) {
        if (mediaPath.length > 50 && !mediaPath.startsWith("http")) {
            // Attempt Base64 decode
            try {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val decodedBytes = android.util.Base64.decode(mediaPath, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
                bitmapState = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val model = when (mediaPath) {
        "MOCK_AVATAR_SAJIB" -> "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
        "MOCK_AVATAR_MARIYA" -> "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"
        "MOCK_AVATAR_BARTA" -> "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=150&q=80"
        "MOCK_IMG_COX" -> "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"
        "MOCK_IMG_TEA" -> "https://images.unsplash.com/photo-1555555555-5c5c36de690d?auto=format&fit=crop&w=800&q=80"
        "MOCK_IMG_WELCOME" -> "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=800&q=80"
        "MOCK_REEL_RATARGUL" -> "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=800&q=80"
        "MOCK_REEL_RAIN" -> "https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?auto=format&fit=crop&w=800&q=80"
        "MOCK_STORY_1" -> "https://images.unsplash.com/photo-1513151233558-d860c5398176?auto=format&fit=crop&w=800&q=80"
        "MOCK_STORY_2" -> "https://images.unsplash.com/photo-1472214222541-d510753a49f8?auto=format&fit=crop&w=800&q=80"
        else -> mediaPath
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmapState != null) {
            Image(
                bitmap = bitmapState!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (model == "DEFAULT_AVATAR" || model.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.6f))
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                error = painterResource(id = android.R.drawable.ic_menu_gallery) // generic fallback
            )
        }
    }
}

// Drawables fallback helper
@Composable
fun painterResource(id: Int) = androidx.compose.ui.res.painterResource(id)

// --- TOP APP BAR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    viewModel: BartaViewModel,
    strings: AppStrings,
    onSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        colors = listOf(InstaPurple, InstaPink, InstaOrange)
                    )
                )
            )
        },
        actions = {
            // Notification Badge Button
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.testTag("top_notifications_button")
                ) {
                    Icon(
                        imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = if (unreadCount > 0) InstaPink else MaterialTheme.colorScheme.onBackground
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, end = 4.dp)
                            .size(16.dp)
                            .background(Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("top_settings_button")
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = Modifier.border(0.dp, Color.Transparent)
    )
}

// --- BOTTOM NAVIGATION BAR ---
@Composable
fun MainBottomBar(
    currentSubScreen: SubScreen,
    strings: AppStrings,
    onTabSelected: (SubScreen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.Home,
            onClick = { onTabSelected(SubScreen.Home) },
            icon = { Icon(if (currentSubScreen == SubScreen.Home) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = strings.home) },
            label = { Text(strings.home, fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_home_tab")
        )
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.Explore,
            onClick = { onTabSelected(SubScreen.Explore) },
            icon = { Icon(Icons.Default.Search, contentDescription = strings.explore) },
            label = { Text(strings.explore, fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_explore_tab")
        )
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.CreatePost,
            onClick = { onTabSelected(SubScreen.CreatePost) },
            icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = strings.createPost) },
            label = { Text(strings.createPost, fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_create_tab")
        )
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.Notifications,
            onClick = { onTabSelected(SubScreen.Notifications) },
            icon = {
                Icon(
                    imageVector = if (currentSubScreen == SubScreen.Notifications) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = strings.notifications
                )
            },
            label = { Text(strings.notifications, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_notifications_tab")
        )
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.Reels,
            onClick = { onTabSelected(SubScreen.Reels) },
            icon = { Icon(if (currentSubScreen == SubScreen.Reels) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, contentDescription = strings.reels) },
            label = { Text(strings.reels, fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_reels_tab")
        )
        NavigationBarItem(
            selected = currentSubScreen == SubScreen.Profile,
            onClick = { onTabSelected(SubScreen.Profile) },
            icon = { Icon(if (currentSubScreen == SubScreen.Profile) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = strings.profile) },
            label = { Text(strings.profile, fontSize = 10.sp) },
            modifier = Modifier.testTag("nav_profile_tab")
        )
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(viewModel: BartaViewModel, strings: AppStrings) {
    val posts by viewModel.allPosts.collectAsState()
    val stories by viewModel.activeStories.collectAsState()
    val suggestedUsers by viewModel.suggestedUsers.collectAsState()

    val regularPosts = remember(posts) { posts.filter { !it.isVideo } }
    val trendingHashtags = remember { listOf("#CoxsBazar", "#Sylhet", "#TechBD", "#BartaFeed", "#BanglaPost") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Horizontal Stories tray
        item {
            StoryTraySection(viewModel, stories, strings)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }

        // Trending Hashtags Row
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = strings.trendingHashtags,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trendingHashtags) { tag ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable {
                                viewModel.navigateToSubScreen(SubScreen.Explore)
                            }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Suggested Users Carousel (shown if available)
        if (suggestedUsers.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = strings.suggestedForYou,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(suggestedUsers) { user ->
                            val isFollowing by viewModel.isFollowingFlow(user.username).collectAsState(initial = false)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { viewModel.viewUserProfile(user) }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        BartaMediaLoader(mediaPath = user.profilePicture, modifier = Modifier.fillMaxSize())
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = user.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.toggleFollowUser(user.username) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowing) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isFollowing) strings.following else strings.follow,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (regularPosts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(strings.emptyPosts, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(regularPosts, key = { it.id }) { post ->
                PostCard(viewModel = viewModel, post = post, strings = strings)
            }
        }
    }
}

// Multi size Modifier extension
fun Modifier.size(size: Int) = this.size(size.dp)
fun Modifier.size(size: Float) = this.size(size.dp)

// --- STORIES TRAY ---
@Composable
fun StoryTraySection(viewModel: BartaViewModel, stories: List<Story>, strings: AppStrings) {
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Setup story picker
    val storyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val scaled = viewModel.decodeAndResizeUri(it, 800)
                if (scaled != null) {
                    viewModel.addStory(scaled, false, "শুভ মুহূর্ত!")
                }
            }
        }
    }

    // Group stories by user
    val groupedStories = stories.groupBy { it.username }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Add Own Story slot
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { storyPickerLauncher.launch("image/*") }
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        BartaMediaLoader(
                            mediaPath = currentUser?.profilePicture ?: "DEFAULT_AVATAR",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(AccentBlue, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.addStory, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(strings.addStory, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Loop other users' stories
        items(groupedStories.keys.toList()) { username ->
            val userStories = groupedStories[username] ?: emptyList()
            val firstStory = userStories.first()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { viewModel.viewStoryForUser(username) }
                    .testTag("story_avatar_$username")
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                            ),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                ) {
                    BartaMediaLoader(
                        mediaPath = firstStory.userProfilePicture,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "@$username", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- POST CARD ---
@Composable
fun PostCard(viewModel: BartaViewModel, post: Post, strings: AppStrings) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()

    val isLiked by viewModel.observeIsPostLiked(post.id).collectAsState(initial = false)
    val isSaved by viewModel.observeIsPostSaved(post.id).collectAsState(initial = false)
    val isMuted by viewModel.isMutedFlow(post.username).collectAsState(initial = false)

    // Animated heart floating for double tap
    var showHeartAnim by remember { mutableStateOf(false) }

    // Dialog States
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showEditCaptionDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(post.id) {
        viewModel.incrementPostViewCount(post.id)
    }

    if (isMuted) {
        // Muted post notice banner or hidden
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            if (post.username != currentUser?.username) {
                                coroutineScope.launch {
                                    val db = BartaDatabase.getDatabase(viewModel.getApplication())
                                    val target = db.userDao().getUserByUsername(post.username)
                                    if (target != null) {
                                        viewModel.viewUserProfile(target)
                                    }
                                }
                            } else {
                                viewModel.navigateToSubScreen(SubScreen.Profile)
                            }
                        }
                ) {
                    BartaMediaLoader(mediaPath = post.userProfilePicture, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (post.username != currentUser?.username) {
                                coroutineScope.launch {
                                    val db = BartaDatabase.getDatabase(viewModel.getApplication())
                                    val target = db.userDao().getUserByUsername(post.username)
                                    if (target != null) {
                                        viewModel.viewUserProfile(target)
                                    }
                                }
                            } else {
                                viewModel.navigateToSubScreen(SubScreen.Profile)
                            }
                        }
                ) {
                    Text(text = post.userFullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (post.location.isNotEmpty()) {
                        Text(text = post.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Options Menu (MoreVert)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (post.username == currentUser?.username) {
                            DropdownMenuItem(
                                text = { Text(strings.editPostCaption) },
                                onClick = {
                                    menuExpanded = false
                                    showEditCaptionDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.deletePostLabel) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.deletePost(post)
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(strings.reportPost) },
                                onClick = {
                                    menuExpanded = false
                                    showReportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("${strings.block} @${post.username}") },
                                onClick = {
                                    menuExpanded = false
                                    showBlockConfirmDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = Color.Red) }
                            )
                            DropdownMenuItem(
                                text = { Text("${strings.mute} @${post.username}") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.muteUser(post.username)
                                    android.widget.Toast.makeText(context, "@${post.username} (${strings.mute})", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Outlined.VolumeOff, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            // Image/Video Body with Double Tap gesture detector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black)
                    .pointerInput(post.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                viewModel.toggleLike(post.id)
                                showHeartAnim = true
                                coroutineScope.launch {
                                    delay(800)
                                    showHeartAnim = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                BartaMediaLoader(mediaPath = post.mediaPaths, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

                // Big white pulse heart animation
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartAnim,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(300)) + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Liked",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(100.dp)
                    )
                }
            }

            // Interactive Buttons (Like, Comment, Share, Save)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleLike(post.id) },
                    modifier = Modifier.testTag("like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) InstaPink else MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { viewModel.openCommentsForPost(post.id) },
                    modifier = Modifier.testTag("comments_button_${post.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comments")
                }

                IconButton(
                    onClick = {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "${post.caption}\n\n- Posted by @${post.username} on Barta App")
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, strings.share))
                    },
                    modifier = Modifier.testTag("share_button_${post.id}")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share")
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { viewModel.toggleSave(post.id) },
                    modifier = Modifier.testTag("save_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (isSaved) InstaOrange else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Like / Comment / View statistics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${post.likeCount} ${strings.doubleTapTip.split(" ").last()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${post.viewCount} ${strings.views}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "@${post.username} ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = post.caption,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (post.commentCount > 0) {
                    Text(
                        text = "${strings.commentsTitle} (${post.commentCount})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable { viewModel.openCommentsForPost(post.id) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showReportDialog) {
        ReportDialog(
            title = strings.reportPost,
            strings = strings,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                viewModel.reportPost(post.id, reason, details)
                android.widget.Toast.makeText(context, strings.reportSubmitted, android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showBlockConfirmDialog) {
        BlockConfirmDialog(
            username = post.username,
            profilePicture = post.userProfilePicture,
            strings = strings,
            onDismiss = { showBlockConfirmDialog = false },
            onConfirm = {
                viewModel.blockUser(post.username)
                android.widget.Toast.makeText(context, "@${post.username} blocked", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEditCaptionDialog) {
        var editCaptionText by remember { mutableStateOf(post.caption) }
        AlertDialog(
            onDismissRequest = { showEditCaptionDialog = false },
            title = { Text(strings.editPostCaption, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editCaptionText,
                    onValueChange = { editCaptionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editPostCaption(post, editCaptionText)
                        showEditCaptionDialog = false
                    }
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCaptionDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

// --- EXPLORE SCREEN ---
@Composable
fun ExploreScreen(viewModel: BartaViewModel, strings: AppStrings) {
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val posts by viewModel.allPosts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(strings.search) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explore_search_bar")
                .padding(bottom = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        AnimatedContent(
            targetState = query.isNotBlank(),
            label = "ExploreModes"
        ) { isSearching ->
            if (isSearching) {
                // Search result of users
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.viewUserProfile(user) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            ) {
                                BartaMediaLoader(mediaPath = user.profilePicture, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = user.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "@${user.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // Grid of popular posts (Explore wall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(posts) { post ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    viewModel.openCommentsForPost(post.id) // view detail of post via comments screen
                                }
                        ) {
                            BartaMediaLoader(mediaPath = post.mediaPaths, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

// --- CREATE POST SCREEN ---
@Composable
fun CreatePostScreen(viewModel: BartaViewModel, strings: AppStrings) {
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val scaled = viewModel.decodeAndResizeUri(it, 640)
                if (scaled != null) {
                    selectedBitmap = scaled
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.createPost,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Select Photo Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { imagePicker.launch("image/*") }
                .testTag("create_post_picker"),
            contentAlignment = Alignment.Center
        ) {
            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, size = 48.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.selectMedia, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text(strings.caption) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_post_caption_input")
                .padding(bottom = 12.dp),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text(strings.location) },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_post_location_input")
                .padding(bottom = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (isUploading) {
            Text(strings.progressUploading, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }

        Button(
            onClick = {
                val bitmap = selectedBitmap
                if (bitmap != null) {
                    coroutineScope.launch {
                        isUploading = true
                        val success = viewModel.createNewPost(caption, location, bitmap)
                        isUploading = false
                        if (success) {
                            // Clear inputs
                            caption = ""
                            location = ""
                            selectedBitmap = null
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("create_post_submit_button"),
            shape = RoundedCornerShape(12.dp),
            enabled = selectedBitmap != null && !isUploading
        ) {
            Text(strings.post, fontWeight = FontWeight.Bold)
        }
    }
}

// Custom size icon helper
@Composable
fun Icon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(size), tint = tint)
}

// --- REELS SCREEN ---
@Composable
fun ReelsScreen(viewModel: BartaViewModel, strings: AppStrings) {
    val posts by viewModel.allPosts.collectAsState()
    val reels = remember(posts) { posts.filter { it.isVideo } }
    val coroutineScope = rememberCoroutineScope()

    if (reels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyPosts)
        }
    } else {
        // Simple swipeable vertical video deck (mimicking reels list)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(reels, key = { it.id }) { reel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.BottomStart
                ) {
                    // Vertical visual simulation thumbnail
                    BartaMediaLoader(
                        mediaPath = reel.mediaPaths,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Black gradient overlay for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )

                    // Interactive Reels Overlay Buttons (Right column)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 24.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleLike(reel.id) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = InstaPink)
                        }
                        Text(reel.likeCount.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        IconButton(
                            onClick = { viewModel.openCommentsForPost(reel.id) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comments", tint = Color.White)
                        }
                        Text(reel.commentCount.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Reels Text Details (Bottom Left)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .fillMaxWidth(0.75f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                            ) {
                                BartaMediaLoader(mediaPath = reel.userProfilePicture, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "@${reel.username}", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = reel.caption, color = Color.White, fontSize = 13.sp)
                        if (reel.location.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, size = 12.dp, tint = Color.LightGray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = reel.location, color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PROFILE SCREEN ---
@Composable
fun ProfileScreen(
    viewModel: BartaViewModel,
    user: User?,
    strings: AppStrings,
    isOwnProfile: Boolean
) {
    if (user == null) return

    val observedUser by viewModel.repository.observeUser(user.username).collectAsState(initial = user)
    val activeUser = observedUser ?: user

    val posts by viewModel.allPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()

    val myPosts = remember(posts, activeUser.username) { posts.filter { it.username == activeUser.username } }
    val followCount by viewModel.getFollowersCount(activeUser.username).collectAsState(initial = activeUser.followersCount)
    val followingCount by viewModel.getFollowingCount(activeUser.username).collectAsState(initial = activeUser.followingCount)

    var isEditProfileOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = My Posts, 1 = Saved, 2 = About Me

    val coroutineScope = rememberCoroutineScope()
    
    // Real-time flow for follow status
    val isFollowing by viewModel.isFollowingFlow(activeUser.username).collectAsState(initial = false)

    var showFollowersListForUser by remember { mutableStateOf<String?>(null) }
    var showFollowingListForUser by remember { mutableStateOf<String?>(null) }
    var showUnfollowConfirmForUser by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        // Profile Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    BartaMediaLoader(mediaPath = activeUser.profilePicture, modifier = Modifier.fillMaxSize())
                }

                // Stats row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStatItem(count = myPosts.size, label = strings.myPosts.split(" ").last())
                    ProfileStatItem(
                        count = followCount,
                        label = strings.followers,
                        onClick = { showFollowersListForUser = activeUser.username }
                    )
                    ProfileStatItem(
                        count = followingCount,
                        label = strings.following,
                        onClick = { showFollowingListForUser = activeUser.username }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bio & Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(text = activeUser.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "@${activeUser.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = activeUser.bio, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                if (activeUser.aboutSection.isNotEmpty()) {
                    Text(
                        text = "📍 " + activeUser.aboutSection,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions (Edit profile or Follow/Unfollow)
            if (isOwnProfile) {
                Button(
                    onClick = { isEditProfileOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.editProfile, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (isFollowing) {
                            showUnfollowConfirmForUser = activeUser
                        } else {
                            viewModel.followUser(activeUser.username)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_follow_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isFollowing) strings.unfollow else strings.follow,
                        fontWeight = FontWeight.Bold,
                        color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                }
            }
        }

        // Section Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.myPosts, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            if (isOwnProfile) {
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(strings.savedPostsLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(strings.aboutSection, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        when (selectedTab) {
            0 -> {
                // My Posts Grid
                if (myPosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.emptyPosts)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp), // fixed bounding height for nesting in scrollable Column
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(myPosts) { post ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { viewModel.openCommentsForPost(post.id) }
                            ) {
                                BartaMediaLoader(mediaPath = post.mediaPaths, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            1 -> {
                // Saved Posts Grid (Own profile only)
                if (savedPosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.emptyPosts)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(savedPosts) { post ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { viewModel.openCommentsForPost(post.id) }
                            ) {
                                BartaMediaLoader(mediaPath = post.mediaPaths, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            2 -> {
                // About Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = strings.aboutSection, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "🎂 ${strings.dob}: ${activeUser.dob}", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "📱 ${strings.phone}: ${activeUser.phoneNumber}", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "✍️ Bio: ${activeUser.bio}", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Edit Profile Overlay Modal
    if (isEditProfileOpen && isOwnProfile) {
        EditProfileDialog(
            viewModel = viewModel,
            user = activeUser,
            strings = strings,
            onDismiss = { isEditProfileOpen = false }
        )
    }

    // Followers list dialog
    val followersUser = showFollowersListForUser
    if (followersUser != null) {
        FollowListDialog(
            viewModel = viewModel,
            username = followersUser,
            showFollowers = true,
            strings = strings,
            onDismiss = { showFollowersListForUser = null },
            onUserClick = { clickedUser ->
                viewModel.viewUserProfile(clickedUser)
            }
        )
    }

    // Following list dialog
    val followingUser = showFollowingListForUser
    if (followingUser != null) {
        FollowListDialog(
            viewModel = viewModel,
            username = followingUser,
            showFollowers = false,
            strings = strings,
            onDismiss = { showFollowingListForUser = null },
            onUserClick = { clickedUser ->
                viewModel.viewUserProfile(clickedUser)
            }
        )
    }

    // Unfollow Confirmation dialog
    val unfollowUser = showUnfollowConfirmForUser
    if (unfollowUser != null) {
        UnfollowConfirmDialog(
            username = unfollowUser.username,
            profilePicture = unfollowUser.profilePicture,
            strings = strings,
            onDismiss = { showUnfollowConfirmForUser = null },
            onConfirm = {
                viewModel.unfollowUser(unfollowUser.username)
            }
        )
    }
}

@Composable
fun ProfileStatItem(count: Int, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(8.dp)
        } else {
            Modifier.padding(8.dp)
        }
    ) {
        Text(text = count.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FollowListDialog(
    viewModel: BartaViewModel,
    username: String,
    showFollowers: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onUserClick: (User) -> Unit
) {
    val followers by viewModel.getFollowersList(username).collectAsState(initial = emptyList())
    val following by viewModel.getFollowingList(username).collectAsState(initial = emptyList())
    val currentUser by viewModel.currentUser.collectAsState()
    
    val userList = if (showFollowers) followers else following
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(userList, searchQuery) {
        if (searchQuery.isBlank()) {
            userList
        } else {
            userList.filter {
                it.username.contains(searchQuery, ignoreCase = true) ||
                        it.fullName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showFollowers) strings.followersList else strings.followingList,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancel,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.search, fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("follow_list_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (showFollowers) Icons.Default.PeopleOutline else Icons.Default.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showFollowers) strings.emptyFollowers else strings.emptyFollowing,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredList) { userItem ->
                            val isMe = currentUser?.username == userItem.username
                            val isFollowingItem by viewModel.isFollowingFlow(userItem.username).collectAsState(initial = false)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onUserClick(userItem)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                ) {
                                    BartaMediaLoader(mediaPath = userItem.profilePicture, modifier = Modifier.fillMaxSize())
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // User details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userItem.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "@${userItem.username}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Follow/Unfollow Action Button
                                if (!isMe) {
                                    Button(
                                        onClick = {
                                            viewModel.toggleFollowUser(userItem.username)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowingItem) {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        ),
                                        modifier = Modifier.defaultMinSize(minWidth = 80.dp)
                                    ) {
                                        Text(
                                            text = if (isFollowingItem) strings.unfollow else strings.follow,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isFollowingItem) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                Color.White
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnfollowConfirmDialog(
    username: String,
    profilePicture: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BartaMediaLoader(mediaPath = profilePicture, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "@$username",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Text(
                text = strings.confirmUnfollow,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(strings.unfollow, color = Color.Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

// --- EDIT PROFILE DIALOG ---
@Composable
fun EditProfileDialog(
    viewModel: BartaViewModel,
    user: User,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var bio by remember { mutableStateOf(user.bio) }
    var aboutSection by remember { mutableStateOf(user.aboutSection) }
    var newBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val scaled = viewModel.decodeAndResizeUri(it, 300)
                if (scaled != null) {
                    newBitmap = scaled
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editProfile, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular edit image slot
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePicker.launch("image/*") }
                ) {
                    if (newBitmap != null) {
                        Image(bitmap = newBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        BartaMediaLoader(mediaPath = user.profilePicture, modifier = Modifier.fillMaxSize())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(strings.fullName) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(strings.bio) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = aboutSection,
                    onValueChange = { aboutSection = it },
                    label = { Text(strings.aboutSection) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.updateProfileInfo(fullName, bio, aboutSection, newBitmap)
                        onDismiss()
                    }
                }
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

// --- SETTINGS DIALOG ---
@Composable
fun SettingsDialog(
    viewModel: BartaViewModel,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val isPrivateAccount by viewModel.isPrivateAccount.collectAsState()
    val isRestrictedAccount by viewModel.isRestrictedAccount.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settings, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Language selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateLanguage(
                                if (currentLanguage == Language.BANGLA) Language.ENGLISH else Language.BANGLA
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.languageToggleLabel, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (currentLanguage == Language.BANGLA) "Active: বাংলা" else "Active: English",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Private Account Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(strings.privateAccount, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = isPrivateAccount,
                        onCheckedChange = { viewModel.togglePrivateAccount() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Restrict Account Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(strings.restrictAccount, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = isRestrictedAccount,
                        onCheckedChange = { viewModel.toggleRestrictAccount() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Logout selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            viewModel.logout()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(strings.logout, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

// --- NOTIFICATIONS DIALOG & SCREEN ---
@Composable
fun NotificationsScreen(
    viewModel: BartaViewModel,
    strings: AppStrings
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.markNotificationsAsRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.notifications,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (notifications.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllNotifications() }) {
                    Text(strings.clearAll, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.notificationsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!notification.isRead) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            ) {
                                BartaMediaLoader(mediaPath = notification.senderProfilePicture, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "@${notification.senderUsername} ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = notification.text,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = android.text.format.DateUtils.getRelativeTimeSpanString(notification.timestamp).toString(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    viewModel: BartaViewModel,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.markNotificationsAsRead()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.notifications, fontWeight = FontWeight.Bold)
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllNotifications() }) {
                        Text(strings.clearAll, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(strings.notificationsEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (!notification.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            ) {
                                BartaMediaLoader(mediaPath = notification.senderProfilePicture, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row {
                                    Text(text = "@${notification.senderUsername} ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = notification.text, fontSize = 13.sp)
                                }
                                Text(
                                    text = android.text.format.DateUtils.getRelativeTimeSpanString(notification.timestamp).toString(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

// --- COMMENTS DIALOG / BOTTOM SHEET ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsOverlay(
    viewModel: BartaViewModel,
    postId: Int,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val comments by viewModel.activePostComments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(strings.commentsTitle, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            },
            bottomBar = {
                // Interactive bottom comment submit bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(strings.commentPlaceholder) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input_bar"),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.addCommentToActivePost(commentText)
                                commentText = ""
                            }
                        },
                        modifier = Modifier.testTag("comment_submit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো মন্তব্য নেই। প্রথম মন্তব্যটি করুন!")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            val isCommentLiked by viewModel.observeIsCommentLiked(comment.id).collectAsState(initial = false)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                ) {
                                    BartaMediaLoader(mediaPath = comment.userProfilePicture, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "@${comment.username} ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = android.text.format.DateUtils.getRelativeTimeSpanString(comment.timestamp).toString(),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    Text(text = comment.commentText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))

                                    // Reply button
                                    Text(
                                        text = strings.replyLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable {
                                                commentText = "@${comment.username} "
                                            }
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.toggleCommentLike(comment.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCommentLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like comment",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isCommentLiked) InstaPink else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (comment.likeCount > 0) {
                                        Text(
                                            text = comment.likeCount.toString(),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- STORY VIEWER OVERLAY ---
@Composable
fun StoryViewerOverlay(
    viewModel: BartaViewModel,
    username: String,
    onDismiss: () -> Unit
) {
    val stories by viewModel.activeStories.collectAsState()
    val userStories = remember(stories, username) { stories.filter { it.username == username } }

    if (userStories.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    val currentStory = userStories[currentIndex]

    // Visual Story progress tickers
    var progress by remember(currentIndex) { mutableStateOf(0f) }

    LaunchedEffect(currentIndex) {
        progress = 0f
        while (progress < 1f) {
            delay(50)
            progress += 0.01f
        }
        if (currentIndex < userStories.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Full Screen Vertical/Horizontal media background
            BartaMediaLoader(
                mediaPath = currentStory.mediaPath,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Overlays: Progress tickers, user details, story text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                // Tickers (Instagram style progress lines)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    userStories.forEachIndexed { index, _ ->
                        val lineProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { lineProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }

                // User details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    ) {
                        BartaMediaLoader(mediaPath = currentStory.userProfilePicture, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "@${currentStory.username}", color = Color.White, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Custom Overlay Caption/Story message
                if (currentStory.text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = currentStory.text,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))
            }

            // Tap screen areas: Left tap (back story), Right tap (next story)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (currentIndex > 0) currentIndex-- else onDismiss()
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (currentIndex < userStories.size - 1) currentIndex++ else onDismiss()
                        }
                )
            }
        }
    }
}

// --- REPORT DIALOG ---
@Composable
fun ReportDialog(
    title: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf(
        strings.reasonSpam,
        strings.reasonHarassment,
        strings.reasonInappropriate,
        strings.reasonFake,
        strings.reasonOther
    )
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = strings.reportReason,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = reason, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text("বিস্তারিত লিখুন (ঐচ্ছিক)...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedReason, details)
                    onDismiss()
                }
            ) {
                Text(strings.submit)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

// --- BLOCK CONFIRM DIALOG ---
@Composable
fun BlockConfirmDialog(
    username: String,
    profilePicture: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BartaMediaLoader(mediaPath = profilePicture, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${strings.block} @$username",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Text(
                text = strings.blockUserConfirm,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(strings.block, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
