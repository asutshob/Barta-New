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
    object Reels : SubScreen()
    object Profile : SubScreen()
}

class BartaViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BartaRepository(application)

    // Screen Navigation
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentSubScreen = MutableStateFlow<SubScreen>(SubScreen.Home)
    val currentSubScreen: StateFlow<SubScreen> = _currentSubScreen.asStateFlow()

    // Localization
    val currentLanguage = LocalizationManager.currentLanguage

    // Active States
    val currentUser = repository.currentUserState

    // Live Lists
    val allPosts = repository.getAllPosts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val activeStories = repository.getActiveStories().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val notifications = repository.getNotifications().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val savedPosts = repository.getSavedPosts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        viewModelScope.launch {
            repository.toggleFollow(username)
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

    fun getFollowersCount(username: String): Flow<Int> = repository.getFollowersCount(username)
    fun getFollowingCount(username: String): Flow<Int> = repository.getFollowingCount(username)

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
    fun clearNotifications() {
        viewModelScope.launch {
            repository.markNotificationsAsRead()
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
