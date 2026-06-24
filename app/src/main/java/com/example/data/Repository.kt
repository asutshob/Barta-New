package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class BartaRepository(private val context: Context) {
    private val db = BartaDatabase.getDatabase(context)
    private val userDao = db.userDao()
    private val postDao = db.postDao()
    private val commentDao = db.commentDao()
    private val storyDao = db.storyDao()
    private val notificationDao = db.notificationDao()
    private val followDao = db.followDao()
    private val savedPostDao = db.savedPostDao()
    private val likeDao = db.likeDao()

    private val _currentUserState = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUserState.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("barta_prefs", Context.MODE_PRIVATE)

    init {
        // Load logged in user on startup
        val savedUsername = sharedPrefs.getString("logged_in_user", null)
        if (savedUsername != null) {
            // Retrieve user synchronously in background or asynchronously
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val user = userDao.getUserByUsername(savedUsername)
                _currentUserState.value = user
            }
        }
    }

    // --- Authentication ---
    suspend fun registerUser(
        username: String,
        fullName: String,
        dob: String,
        profilePicBase64: String,
        bio: String,
        phoneNumber: String,
        password: String,
        aboutSection: String = ""
    ): Boolean {
        // Check if username already exists
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        val existing = userDao.getUserByUsername(cleanUsername)
        if (existing != null) return false

        val newUser = User(
            username = cleanUsername,
            fullName = fullName,
            dob = dob,
            profilePicture = profilePicBase64.ifEmpty { getDefaultAvatar() },
            bio = bio,
            phoneNumber = phoneNumber,
            password = password,
            aboutSection = aboutSection
        )
        userDao.insertUser(newUser)
        loginUser(cleanUsername, password)
        return true
    }

    suspend fun loginUser(phoneNumberOrUsername: String, password: String): Boolean {
        val cleanInput = phoneNumberOrUsername.trim().lowercase().removePrefix("@")
        // Try searching by username first
        var user = userDao.getUserByUsername(cleanInput)
        // If not found, search by phone number in DB
        if (user == null) {
            // Search all users for matching phone (simple fallback scan)
            // For simplicity, let's search via query or exact phone check
            val searchResults = userDao.searchUsers(cleanInput).first()
            user = searchResults.find { it.phoneNumber == phoneNumberOrUsername || it.username == cleanInput }
        }

        if (user != null && user.password == password) {
            _currentUserState.value = user
            sharedPrefs.edit().putString("logged_in_user", user.username).apply()
            return true
        }
        return false
    }

    fun logoutUser() {
        _currentUserState.value = null
        sharedPrefs.edit().remove("logged_in_user").apply()
    }

    suspend fun updateProfile(
        fullName: String,
        bio: String,
        aboutSection: String,
        profilePicBase64: String?
    ): Boolean {
        val currentUser = _currentUserState.value ?: return false
        val updatedUser = currentUser.copy(
            fullName = fullName,
            bio = bio,
            aboutSection = aboutSection,
            profilePicture = profilePicBase64 ?: currentUser.profilePicture
        )
        userDao.updateUser(updatedUser)
        _currentUserState.value = updatedUser
        return true
    }

    fun observeUser(username: String): Flow<User?> {
        return userDao.observeUserByUsername(username)
    }

    fun searchUsers(query: String): Flow<List<User>> {
        return userDao.searchUsers(query)
    }

    // --- Social (Follow/Unfollow) ---
    suspend fun isFollowing(follower: String, following: String): Boolean {
        return followDao.getFollowRecord(follower, following) != null
    }

    suspend fun toggleFollow(followingUsername: String) {
        val currentUser = _currentUserState.value ?: return
        val record = followDao.getFollowRecord(currentUser.username, followingUsername)
        if (record != null) {
            followDao.deleteFollow(record)
            // Update cache counters
            updateFollowerCounters(currentUser.username, followingUsername, -1)
        } else {
            val newFollow = Follow(
                followerUsername = currentUser.username,
                followingUsername = followingUsername
            )
            followDao.insertFollow(newFollow)
            updateFollowerCounters(currentUser.username, followingUsername, 1)

            // Create notification
            createNotification(
                recipient = followingUsername,
                type = "FOLLOW",
                text = "আপনাকে অনুসরণ করা শুরু করেছেন।" // "Started following you."
            )
        }
    }

    private suspend fun updateFollowerCounters(follower: String, following: String, delta: Int) {
        val fUser = userDao.getUserByUsername(follower)
        if (fUser != null) {
            userDao.updateUser(fUser.copy(followingCount = (fUser.followingCount + delta).coerceAtLeast(0)))
            if (currentUserState.value?.username == follower) {
                _currentUserState.value = userDao.getUserByUsername(follower)
            }
        }
        val tUser = userDao.getUserByUsername(following)
        if (tUser != null) {
            userDao.updateUser(tUser.copy(followersCount = (tUser.followersCount + delta).coerceAtLeast(0)))
        }
    }

    fun getFollowersCount(username: String): Flow<Int> = userDao.getFollowersCount(username)
    fun getFollowingCount(username: String): Flow<Int> = userDao.getFollowingCount(username)
    fun getFollowingList(username: String): Flow<List<String>> = followDao.getFollowingList(username)

    // --- Posts ---
    fun getAllPosts(): Flow<List<Post>> = postDao.getAllPosts()
    fun getPostsByUsername(username: String): Flow<List<Post>> = postDao.getPostsByUsername(username)
    fun searchPosts(query: String): Flow<List<Post>> = postDao.searchPosts(query)

    suspend fun createPost(caption: String, location: String, mediaBase64: String, isVideo: Boolean = false): Boolean {
        val currentUser = _currentUserState.value ?: return false
        val newPost = Post(
            username = currentUser.username,
            userFullName = currentUser.fullName,
            userProfilePicture = currentUser.profilePicture,
            caption = caption,
            location = location,
            mediaPaths = mediaBase64,
            isVideo = isVideo
        )
        postDao.insertPost(newPost)
        return true
    }

    suspend fun deletePost(post: Post): Boolean {
        val currentUser = _currentUserState.value ?: return false
        if (post.username == currentUser.username) {
            postDao.deletePost(post)
            return true
        }
        return false
    }

    suspend fun updatePostCaption(post: Post, newCaption: String): Boolean {
        val currentUser = _currentUserState.value ?: return false
        if (post.username == currentUser.username) {
            postDao.updatePost(post.copy(caption = newCaption))
            return true
        }
        return false
    }

    // --- Likes ---
    suspend fun isPostLiked(postId: Int): Boolean {
        val currentUser = _currentUserState.value ?: return false
        return likeDao.getLike(currentUser.username, postId) != null
    }

    suspend fun toggleLike(postId: Int) {
        val currentUser = _currentUserState.value ?: return
        val post = postDao.getPostById(postId) ?: return
        val existingLike = likeDao.getLike(currentUser.username, postId)
        if (existingLike != null) {
            likeDao.deleteLike(existingLike)
            postDao.updatePost(post.copy(likeCount = (post.likeCount - 1).coerceAtLeast(0)))
        } else {
            likeDao.insertLike(Like(username = currentUser.username, postId = postId))
            postDao.updatePost(post.copy(likeCount = post.likeCount + 1))

            if (post.username != currentUser.username) {
                createNotification(
                    recipient = post.username,
                    type = "LIKE",
                    postId = postId,
                    text = "আপনার পোস্টে লাইক দিয়েছেন।" // "Liked your post."
                )
            }
        }
    }

    fun getLikesCountFlow(postId: Int): Flow<Int> = likeDao.getLikesCountForPost(postId)

    // --- Saved Posts ---
    suspend fun isPostSaved(postId: Int): Boolean {
        val currentUser = _currentUserState.value ?: return false
        return savedPostDao.getSavedPost(currentUser.username, postId) != null
    }

    suspend fun toggleSavePost(postId: Int) {
        val currentUser = _currentUserState.value ?: return
        val existingSave = savedPostDao.getSavedPost(currentUser.username, postId)
        if (existingSave != null) {
            savedPostDao.deleteSavedPost(existingSave)
        } else {
            savedPostDao.insertSavedPost(SavedPost(username = currentUser.username, postId = postId))
        }
    }

    fun getSavedPosts(): Flow<List<Post>> {
        val currentUser = _currentUserState.value ?: return MutableStateFlow(emptyList())
        return savedPostDao.getSavedPostsForUser(currentUser.username)
    }

    // --- Comments ---
    fun getCommentsForPost(postId: Int): Flow<List<Comment>> = commentDao.getCommentsForPost(postId)

    suspend fun addComment(postId: Int, commentText: String, parentCommentId: Int? = null, replyToUsername: String? = null): Boolean {
        val currentUser = _currentUserState.value ?: return false
        val post = postDao.getPostById(postId) ?: return false

        val comment = Comment(
            postId = postId,
            username = currentUser.username,
            userProfilePicture = currentUser.profilePicture,
            commentText = commentText,
            parentCommentId = parentCommentId,
            replyToUsername = replyToUsername
        )
        commentDao.insertComment(comment)
        postDao.updatePost(post.copy(commentCount = post.commentCount + 1))

        if (post.username != currentUser.username) {
            val notifyText = if (parentCommentId != null) {
                "আপনার মন্তব্যের উত্তর দিয়েছেন।" // "Replied to your comment."
            } else {
                "আপনার পোস্টে মন্তব্য করেছেন।" // "Commented on your post."
            }
            createNotification(
                recipient = replyToUsername ?: post.username,
                type = if (parentCommentId != null) "REPLY" else "COMMENT",
                postId = postId,
                text = notifyText
            )
        }
        return true
    }

    // --- Stories ---
    fun getActiveStories(): Flow<List<Story>> {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        return storyDao.getActiveStories(cutoff)
    }

    suspend fun addStory(mediaPath: String, isVideo: Boolean = false, text: String = ""): Boolean {
        val currentUser = _currentUserState.value ?: return false
        val story = Story(
            username = currentUser.username,
            userProfilePicture = currentUser.profilePicture,
            mediaPath = mediaPath,
            isVideo = isVideo,
            text = text
        )
        storyDao.insertStory(story)
        return true
    }

    // --- Notifications ---
    fun getNotifications(): Flow<List<Notification>> {
        val currentUser = _currentUserState.value ?: return MutableStateFlow(emptyList())
        return notificationDao.getNotifications(currentUser.username)
    }

    suspend fun markNotificationsAsRead() {
        val currentUser = _currentUserState.value ?: return
        notificationDao.markAllAsRead(currentUser.username)
    }

    private suspend fun createNotification(recipient: String, type: String, postId: Int? = null, text: String) {
        val currentUser = _currentUserState.value ?: return
        if (recipient == currentUser.username) return // Don't notify self
        val notification = Notification(
            recipientUsername = recipient,
            senderUsername = currentUser.username,
            senderProfilePicture = currentUser.profilePicture,
            type = type,
            postId = postId,
            text = text
        )
        notificationDao.insertNotification(notification)
    }

    // --- Image Compression & Helpers ---
    fun compressBitmap(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress bitmap to 60% quality to fit easily as JPEG Base64
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // Default Avatar for pre-populated content
    fun getDefaultAvatar(): String {
        return "DEFAULT_AVATAR"
    }

    // Pre-populate data with lifelike defaults for gorgeous immediate preview!
    suspend fun prepopulateIfEmpty() {
        // Check if users table is empty
        val list = userDao.searchUsers("").first()
        if (list.isEmpty()) {
            // Register standard mock users
            val user1 = User(
                username = "sajib_hasan",
                fullName = "সজিব হাসান (Sajib Hasan)",
                dob = "1999-10-15",
                profilePicture = "MOCK_AVATAR_SAJIB",
                bio = "ফটোগ্রাফি করতে ভালোবাসি। ভ্রমণ আমার শখ। 🌴🇧🇩",
                phoneNumber = "01712345678",
                password = "password123",
                followersCount = 1420,
                followingCount = 380,
                aboutSection = "কুষ্টিয়া, বাংলাদেশ থেকে। গ্রাফিক্স ডিজাইনার ও শৌখিন আলোকচিত্রী।"
            )
            val user2 = User(
                username = "mariya_bte",
                fullName = "মারিয়া রহমান (Mariya Rahman)",
                dob = "2001-04-20",
                profilePicture = "MOCK_AVATAR_MARIYA",
                bio = "চা বাগানের কন্যা। সিলেট আমার ঘর। প্রকৃতিপ্রেমী। ☕️✨",
                phoneNumber = "01812345678",
                password = "password123",
                followersCount = 2310,
                followingCount = 420,
                aboutSection = "সিলেট, বাংলাদেশ। শাহজালাল বিজ্ঞান ও প্রযুক্তি বিশ্ববিদ্যালয়ের শিক্ষার্থী।"
            )
            val user3 = User(
                username = "barta_official",
                fullName = "বার্তা অফিশিয়াল (Barta Official)",
                dob = "2026-01-01",
                profilePicture = "MOCK_AVATAR_BARTA",
                bio = "বার্তা (Barta) অ্যাপ্লিকেশনে আপনাকে স্বাগতম! শেয়ার করুন আপনার আনন্দময় মুহূর্তগুলো। 📸✨",
                phoneNumber = "01912345678",
                password = "password123",
                followersCount = 9999,
                followingCount = 1,
                aboutSection = "বার্তা সোশ্যাল মিডিয়া টিম। আমাদের সাথে যুক্ত থাকুন।"
            )
            userDao.insertUser(user1)
            userDao.insertUser(user2)
            userDao.insertUser(user3)

            // Populate some initial posts
            val post1 = Post(
                username = "sajib_hasan",
                userFullName = "সজিব হাসান (Sajib Hasan)",
                userProfilePicture = "MOCK_AVATAR_SAJIB",
                caption = "কক্সবাজারের নয়নাভিরাম সূর্যাস্ত! প্রকৃতি সত্যি অপরূপ। 🌅🌊 #sunset #travelbd #coxsbazar",
                location = "Cox's Bazar Sea Beach",
                mediaPaths = "MOCK_IMG_COX",
                likeCount = 254,
                commentCount = 3,
                timestamp = System.currentTimeMillis() - 3600000 * 2 // 2 hours ago
            )

            val post2 = Post(
                username = "mariya_bte",
                userFullName = "মারিয়া রহমান (Mariya Rahman)",
                userProfilePicture = "MOCK_AVATAR_MARIYA",
                caption = "শ্রীমঙ্গলের সবুজে ঘেরা চা বাগান। এক কাপ চায়ের সাথে অসাধারণ একটা সকাল কাটলো! 🍵💚 #sreemangal #tea #nature",
                location = "Sreemangal, Sylhet",
                mediaPaths = "MOCK_IMG_TEA",
                likeCount = 412,
                commentCount = 2,
                timestamp = System.currentTimeMillis() - 3600000 * 5 // 5 hours ago
            )

            val post3 = Post(
                username = "barta_official",
                userFullName = "বার্তা অফিশিয়াল (Barta Official)",
                userProfilePicture = "MOCK_AVATAR_BARTA",
                caption = "আমাদের নতুন প্ল্যাটফর্ম 'বার্তা' (Barta) তে আজই যুক্ত হোন এবং আপনার বন্ধুদের সাথে মুহূর্তগুলো শেয়ার করুন। 📸🎥 #barta #newapp #bangladesh",
                location = "Dhaka, Bangladesh",
                mediaPaths = "MOCK_IMG_WELCOME",
                likeCount = 1024,
                commentCount = 1,
                timestamp = System.currentTimeMillis() - 3600000 * 24 // 1 day ago
            )

            // Reels (marked as vertical layouts or simply post flag/captions)
            val reel1 = Post(
                username = "sajib_hasan",
                userFullName = "সজিব হাসান (Sajib Hasan)",
                userProfilePicture = "MOCK_AVATAR_SAJIB",
                caption = "বর্ষায় রাতারগুল জলাবন! এক অপার্থিব অনুভূতি। 🛶🌲🌧️ #reels #ratargul #swampforest",
                location = "Ratargul Swamp Forest, Sylhet",
                mediaPaths = "MOCK_REEL_RATARGUL",
                isVideo = true,
                likeCount = 582,
                commentCount = 12,
                timestamp = System.currentTimeMillis() - 3600000 * 10
            )

            val reel2 = Post(
                username = "mariya_bte",
                userFullName = "মারিয়া রহমান (Mariya Rahman)",
                userProfilePicture = "MOCK_AVATAR_MARIYA",
                caption = "বৃষ্টির দিনে চায়ের কাপে ঝড়! ☔️☕️ #rainyday #vibes #sylhet",
                location = "Sylhet City",
                mediaPaths = "MOCK_REEL_RAIN",
                isVideo = true,
                likeCount = 890,
                commentCount = 15,
                timestamp = System.currentTimeMillis() - 3600000 * 18
            )

            postDao.insertPost(post1)
            postDao.insertPost(post2)
            postDao.insertPost(post3)
            postDao.insertPost(reel1)
            postDao.insertPost(reel2)

            // Comments
            commentDao.insertComment(Comment(postId = 1, username = "mariya_bte", userProfilePicture = "MOCK_AVATAR_MARIYA", commentText = "অসাধারণ ছবি! মনের মতো দৃশ্য। 😍"))
            commentDao.insertComment(Comment(postId = 1, username = "barta_official", userProfilePicture = "MOCK_AVATAR_BARTA", commentText = "চমৎকার আলোকচিত্র, সজিব! আমাদের সাথে শেয়ার করার জন্য ধন্যবাদ।"))
            commentDao.insertComment(Comment(postId = 1, username = "sajib_hasan", userProfilePicture = "MOCK_AVATAR_SAJIB", commentText = "ধন্যবাদ সবাইকে! ❤️", parentCommentId = 1, replyToUsername = "mariya_bte"))

            commentDao.insertComment(Comment(postId = 2, username = "sajib_hasan", userProfilePicture = "MOCK_AVATAR_SAJIB", commentText = "সবুজের সমারোহ! শ্রীমঙ্গলে যাওয়ার ইচ্ছা অনেকদিনের। ✨"))

            // Stories
            storyDao.insertStory(Story(username = "mariya_bte", userProfilePicture = "MOCK_AVATAR_MARIYA", mediaPath = "MOCK_STORY_1", text = "শুভ সকাল! ☀️"))
            storyDao.insertStory(Story(username = "sajib_hasan", userProfilePicture = "MOCK_AVATAR_SAJIB", mediaPath = "MOCK_STORY_2", text = "অন দ্য রোড... 🚗"))
        }
    }
}
