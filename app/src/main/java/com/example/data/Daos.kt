package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    fun observeUserByUsername(username: String): Flow<User?>

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' OR fullName LIKE '%' || :query || '%'")
    fun searchUsers(query: String): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username != :currentUsername AND username NOT IN (SELECT followingUsername FROM follows WHERE followerUsername = :currentUsername) LIMIT 10")
    fun getSuggestedUsers(currentUsername: String): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT COUNT(*) FROM follows WHERE followingUsername = :username")
    fun getFollowersCount(username: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM follows WHERE followerUsername = :username")
    fun getFollowingCount(username: String): Flow<Int>
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE username = :username ORDER BY timestamp DESC")
    fun getPostsByUsername(username: String): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Int): Post?

    @Query("UPDATE posts SET viewCount = viewCount + 1 WHERE id = :postId")
    suspend fun incrementViewCount(postId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)

    @Update
    suspend fun updatePost(post: Post)

    @Delete
    suspend fun deletePost(post: Post)

    @Query("SELECT * FROM posts WHERE caption LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPosts(query: String): Flow<List<Post>>
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getCommentsForPost(postId: Int): Flow<List<Comment>>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun getCommentById(commentId: Int): Comment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)

    @Update
    suspend fun updateComment(comment: Comment)

    @Delete
    suspend fun deleteComment(comment: Comment)
}

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE timestamp > :cutoff ORDER BY timestamp DESC")
    fun getActiveStories(cutoff: Long): Flow<List<Story>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story)

    @Query("DELETE FROM stories WHERE timestamp <= :cutoff")
    suspend fun deleteExpiredStories(cutoff: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE recipientUsername = :recipient ORDER BY timestamp DESC")
    fun getNotifications(recipient: String): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientUsername = :recipient AND isRead = 0")
    fun getUnreadCount(recipient: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientUsername = :recipient")
    suspend fun markAllAsRead(recipient: String)

    @Query("DELETE FROM notifications WHERE recipientUsername = :recipient")
    suspend fun clearAllNotifications(recipient: String)
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM follows WHERE followerUsername = :follower AND followingUsername = :following LIMIT 1")
    suspend fun getFollowRecord(follower: String, following: String): Follow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: Follow)

    @Delete
    suspend fun deleteFollow(follow: Follow)

    @Query("DELETE FROM follows WHERE followerUsername = :follower AND followingUsername = :following")
    suspend fun removeFollowRecord(follower: String, following: String)

    @Query("SELECT followingUsername FROM follows WHERE followerUsername = :follower")
    fun getFollowingList(follower: String): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM follows WHERE followerUsername = :follower AND followingUsername = :following")
    fun observeIsFollowing(follower: String, following: String): Flow<Boolean>

    @Query("SELECT users.* FROM users INNER JOIN follows ON users.username = follows.followerUsername WHERE follows.followingUsername = :username")
    fun getFollowersForUser(username: String): Flow<List<User>>

    @Query("SELECT users.* FROM users INNER JOIN follows ON users.username = follows.followingUsername WHERE follows.followerUsername = :username")
    fun getFollowingForUser(username: String): Flow<List<User>>
}

@Dao
interface SavedPostDao {
    @Query("SELECT * FROM saved_posts WHERE username = :username AND postId = :postId LIMIT 1")
    suspend fun getSavedPost(username: String, postId: Int): SavedPost?

    @Query("SELECT COUNT(*) > 0 FROM saved_posts WHERE username = :username AND postId = :postId")
    fun observeIsSaved(username: String, postId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPost(savedPost: SavedPost)

    @Delete
    suspend fun deleteSavedPost(savedPost: SavedPost)

    @Query("SELECT posts.* FROM posts INNER JOIN saved_posts ON posts.id = saved_posts.postId WHERE saved_posts.username = :username ORDER BY posts.timestamp DESC")
    fun getSavedPostsForUser(username: String): Flow<List<Post>>
}

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes WHERE username = :username AND postId = :postId LIMIT 1")
    suspend fun getLike(username: String, postId: Int): Like?

    @Query("SELECT COUNT(*) > 0 FROM likes WHERE username = :username AND postId = :postId")
    fun observeIsLiked(username: String, postId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: Like)

    @Delete
    suspend fun deleteLike(like: Like)

    @Query("SELECT COUNT(*) FROM likes WHERE postId = :postId")
    fun getLikesCountForPost(postId: Int): Flow<Int>
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: Report)

    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<Report>>
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks WHERE blockerUsername = :blocker AND blockedUsername = :blocked LIMIT 1")
    suspend fun getBlockRecord(blocker: String, blocked: String): Block?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: Block)

    @Delete
    suspend fun deleteBlock(block: Block)

    @Query("SELECT blockedUsername FROM blocks WHERE blockerUsername = :blocker")
    fun getBlockedUsernames(blocker: String): Flow<List<String>>

    @Query("SELECT blockerUsername FROM blocks WHERE blockedUsername = :blocked")
    fun getBlockerUsernames(blocked: String): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM blocks WHERE (blockerUsername = :userA AND blockedUsername = :userB) OR (blockerUsername = :userB AND blockedUsername = :userA)")
    fun observeIsBlockedEitherWay(userA: String, userB: String): Flow<Boolean>
}

@Dao
interface MuteDao {
    @Query("SELECT * FROM mutes WHERE muterUsername = :muter AND mutedUsername = :muted LIMIT 1")
    suspend fun getMuteRecord(muter: String, muted: String): Mute?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMute(mute: Mute)

    @Delete
    suspend fun deleteMute(mute: Mute)

    @Query("SELECT mutedUsername FROM mutes WHERE muterUsername = :muter")
    fun getMutedUsernames(muter: String): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM mutes WHERE muterUsername = :muter AND mutedUsername = :muted")
    fun observeIsMuted(muter: String, muted: String): Flow<Boolean>
}

@Dao
interface CommentLikeDao {
    @Query("SELECT * FROM comment_likes WHERE username = :username AND commentId = :commentId LIMIT 1")
    suspend fun getCommentLike(username: String, commentId: Int): CommentLike?

    @Query("SELECT COUNT(*) > 0 FROM comment_likes WHERE username = :username AND commentId = :commentId")
    fun observeIsCommentLiked(username: String, commentId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommentLike(commentLike: CommentLike)

    @Delete
    suspend fun deleteCommentLike(commentLike: CommentLike)
}
