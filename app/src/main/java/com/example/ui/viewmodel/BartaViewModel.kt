package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.utils.Language
import com.example.ui.utils.LocalizationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Login : Screen()
    object SignupStep1 : Screen()
    object SignupStep2 : Screen()
    data class Welcome(val username: String) : Screen()
    object Main : Screen()
}

sealed class SubScreen {
    object Home : SubScreen()
    object Explore : SubScreen()
    object CreatePost : SubScreen()
    object Notifications : SubScreen()
    object Reels : SubScreen()
    object Profile : SubScreen()
}

class BartaViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BartaRepository(application)
    val followRepository = FollowRepository(application)

    // Screen Navigation
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentSubScreen = MutableStateFlow<SubScreen>(SubScreen.Home)
    val currentSubScreen: StateFlow<SubScreen> = _currentSubScreen.asStateFlow()

    // Localization
    val currentLanguage = LocalizationManager.currentLanguage

    // Active States
    val currentUser = repository.currentUserState

    val isPrivateAccount: StateFlow<Boolean> = currentUser
        .map { it?.isPrivate ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isRestrictedAccount: StateFlow<Boolean> = currentUser
        .map { it?.isRestricted ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Live Lists
    val allPosts = repository.getAllPosts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val activeStories = repository.getActiveStories().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val notifications = repository.getNotifications().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val unreadNotificationCount = repository.getUnreadNotificationCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val savedPosts = repository.getSavedPosts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val suggestedUsers = repository.getSuggestedUsers().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchResults = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.searchUsers("")
            } else {
                repository.searchUsers(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Active Story Viewer State
    private val _viewingStoryUser = MutableStateFlow<String?>(null)
    val viewingStoryUser = _viewingStoryUser.asStateFlow()

    // View Profile of another user (Deep Dive)
    private val _viewingUserProfile = MutableStateFlow<User?>(null)
    val viewingUserProfile = _viewingUserProfile.asStateFlow()

    val viewingUserPosts = _viewingUserProfile
        .flatMapLatest { user ->
            if (user != null) {
                repository.getPostsByUsername(user.username)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Post Comments State
    private val _activeCommentPostId = MutableStateFlow<Int?>(null)
    val activeCommentPostId = _activeCommentPostId.asStateFlow()

    val activePostComments = _activeCommentPostId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getCommentsForPost(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            repository.prepopulateIfEmpty()
            // If user is already logged in, navigate straight to Main
            repository.currentUserState.collect { user ->
                if (user != null) {
                    _currentScreen.value = Screen.Main
                } else if (_currentScreen.value is Screen.Main) {
                    _currentScreen.value = Screen.Login
                }
            }
        }
    }

    // --- Authentication Actions ---
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateToSubScreen(subScreen: SubScreen) {
        // Clear secondary screens when returning/switching tabs
        _viewingUserProfile.value = null
        _currentSubScreen.value = subScreen
    }

    fun updateLanguage(language: Language) {
        LocalizationManager.setLanguage(language)
    }

    suspend fun signup(
        username: String,
        fullName: String,
        dob: String,
        profilePic: Bitmap?,
        bio: String,
        phone: String,
        pass: String
    ): Boolean {
        var base64 = ""
        if (profilePic != null) {
            base64 = repository.compressBitmap(profilePic)
        }
        val success = repository.registerUser(
            username = username,
            fullName = fullName,
            dob = dob,
            profilePicBase64 = base64,
            bio = bio,
            phoneNumber = phone,
            password = pass,
            aboutSection = "কুষ্টিয়া, বাংলাদেশ থেকে।" // default
        )
        if (success) {
            _currentScreen.value = Screen.Welcome(username.trim().lowercase().removePrefix("@"))
        }
        return success
    }

    suspend fun login(phoneOrUsername: String, pass: String): Boolean {
        val success = repository.loginUser(phoneOrUsername, pass)
        if (success) {
            _currentScreen.value = Screen.Main
        }
        return success
    }

    fun logout() {
        repository.logoutUser()
        _currentScreen.value = Screen.Login
        _currentSubScreen.value = SubScreen.Home
    }

    // --- Profile Actions ---
    fun viewUserProfile(user: User) {
        _viewingUserProfile.value = user
    }

    fun clearViewingUserProfile() {
        _viewingUserProfile.value = null
    }

    suspend fun updateProfileInfo(fullName: String, bio: String, about: String, profilePic: Bitmap?): Boolean {
        val base64 = profilePic?.let { repository.compressBitmap(it) }
        val success = repository.updateProfile(fullName, bio, about, base64)
        return success
    }

    // --- Image Safe Decoding & Resizing Helper ---
    suspend fun decodeAndResizeUri(uri: android.net.Uri, maxDim: Int): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val context = getApplication<Application>().applicationContext
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            val width = softwareBitmap.width
            val height = softwareBitmap.height
            val scale = maxDim.toFloat() / Math.max(width, height).toFloat()
            if (scale < 1.0f) {
                val newWidth = (width * scale).toInt()
                val newHeight = (height * scale).toInt()
                Bitmap.createScaledBitmap(softwareBitmap, newWidth, newHeight, true)
            } else {
                softwareBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Post Actions ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun createNewPost(caption: String, location: String, mediaBitmap: Bitmap, isVideo: Boolean = false): Boolean {
        val base64 = repository.compressBitmap(mediaBitmap)
        val success = repository.createPost(caption, location, base64, isVideo)
        if (success) {
            _currentSubScreen.value = SubScreen.Home
        }
        return success
    }

    fun deletePost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post)
        }
    }

    fun editPostCaption(post: Post, newCaption: String) {
        viewModelScope.launch {
            repository.updatePostCaption(post, newCaption)
        }
    }

    fun toggleLike(postId: Int) {
        viewModelScope.launch {
            repository.toggleLike(postId)
        }
    }

    fun toggleSave(postId: Int) {
        viewModelScope.launch {
            repository.toggleSavePost(postId)
        }
    }

    fun toggleFollowUser(username: String) {
        val cur = currentUser.value?.username ?: return
        viewModelScope.launch {
            val isFollowing = followRepository.isFollowing(cur, username).first()
            if (isFollowing) {
                followRepository.unfollowUser(cur, username)
            } else {
                followRepository.followUser(cur, username)
            }
            // Refresh local user viewing if viewing profile
            val activeViewing = _viewingUserProfile.value
            if (activeViewing != null && activeViewing.username == username) {
                val updated = repository.observeUser(username).first()
                if (updated != null) {
                    _viewingUserProfile.value = updated
                }
            }
        }
    }

    fun followUser(targetUsername: String) {
        val cur = currentUser.value?.username ?: return
        viewModelScope.launch {
            followRepository.followUser(cur, targetUsername)
            // Refresh local user viewing if viewing profile
            val activeViewing = _viewingUserProfile.value
            if (activeViewing != null && activeViewing.username == targetUsername) {
                val updated = repository.observeUser(targetUsername).first()
                if (updated != null) {
                    _viewingUserProfile.value = updated
                }
            }
        }
    }

    fun unfollowUser(targetUsername: String) {
        val cur = currentUser.value?.username ?: return
        viewModelScope.launch {
            followRepository.unfollowUser(cur, targetUsername)
            // Refresh local user viewing if viewing profile
            val activeViewing = _viewingUserProfile.value
            if (activeViewing != null && activeViewing.username == targetUsername) {
                val updated = repository.observeUser(targetUsername).first()
                if (updated != null) {
                    _viewingUserProfile.value = updated
                }
            }
        }
    }

    fun isFollowingFlow(username: String): Flow<Boolean> {
        val cur = currentUser.value?.username ?: return flowOf(false)
        return followRepository.isFollowing(cur, username)
    }

    fun getFollowersList(username: String): Flow<List<User>> {
        return followRepository.getFollowers(username)
    }

    fun getFollowingList(username: String): Flow<List<User>> {
        return followRepository.getFollowing(username)
    }

    fun getFollowersCount(username: String): Flow<Int> = followRepository.getFollowersCount(username)
    fun getFollowingCount(username: String): Flow<Int> = followRepository.getFollowingCount(username)

    // --- Stories ---
    fun viewStoryForUser(username: String?) {
        _viewingStoryUser.value = username
    }

    suspend fun addStory(bitmap: Bitmap, isVideo: Boolean = false, text: String = ""): Boolean {
        val base64 = repository.compressBitmap(bitmap)
        return repository.addStory(base64, isVideo, text)
    }

    // --- Comments ---
    fun openCommentsForPost(postId: Int?) {
        _activeCommentPostId.value = postId
    }

    fun addCommentToActivePost(commentText: String, parentCommentId: Int? = null, replyToUsername: String? = null) {
        val postId = _activeCommentPostId.value ?: return
        viewModelScope.launch {
            repository.addComment(postId, commentText, parentCommentId, replyToUsername)
        }
    }

    // --- Notifications ---
    fun markNotificationsAsRead() {
        viewModelScope.launch {
            repository.markNotificationsAsRead()
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    // --- Report, Block, Mute & Privacy Actions ---
    fun reportPost(postId: Int, reason: String, details: String = "") {
        viewModelScope.launch {
            repository.reportPost(postId, reason, details)
        }
    }

    fun reportProfile(username: String, reason: String, details: String = "") {
        viewModelScope.launch {
            repository.reportProfile(username, reason, details)
        }
    }

    fun blockUser(username: String) {
        viewModelScope.launch {
            repository.blockUser(username)
            if (_viewingUserProfile.value?.username == username) {
                _viewingUserProfile.value = null
            }
        }
    }

    fun unblockUser(username: String) {
        viewModelScope.launch {
            repository.unblockUser(username)
        }
    }

    fun isBlockedFlow(username: String): Flow<Boolean> = repository.isBlockedFlow(username)

    fun muteUser(username: String) {
        viewModelScope.launch {
            repository.muteUser(username)
        }
    }

    fun unmuteUser(username: String) {
        viewModelScope.launch {
            repository.unmuteUser(username)
        }
    }

    fun isMutedFlow(username: String): Flow<Boolean> = repository.isMutedFlow(username)

    fun removeFollower(followerUsername: String) {
        viewModelScope.launch {
            repository.removeFollower(followerUsername)
        }
    }

    fun toggleCommentLike(commentId: Int) {
        viewModelScope.launch {
            repository.toggleCommentLike(commentId)
        }
    }

    fun observeIsCommentLiked(commentId: Int): Flow<Boolean> = repository.observeIsCommentLiked(commentId)

    fun observeIsPostLiked(postId: Int): Flow<Boolean> = repository.observeIsPostLiked(postId)

    fun observeIsPostSaved(postId: Int): Flow<Boolean> = repository.observeIsPostSaved(postId)

    fun incrementPostViewCount(postId: Int) {
        viewModelScope.launch {
            repository.incrementPostViewCount(postId)
        }
    }

    fun togglePrivateAccount() {
        viewModelScope.launch {
            repository.togglePrivateAccount()
        }
    }

    fun toggleRestrictAccount() {
        viewModelScope.launch {
            repository.toggleRestrictAccount()
        }
    }

    // Checking if a post is liked/saved dynamically (Flow or suspended)
    suspend fun isPostLiked(postId: Int): Boolean = repository.isPostLiked(postId)
    suspend fun isPostSaved(postId: Int): Boolean = repository.isPostSaved(postId)
    suspend fun isFollowingUser(username: String): Boolean {
        val cur = currentUser.value?.username ?: return false
        return repository.isFollowing(cur, username)
    }
}
