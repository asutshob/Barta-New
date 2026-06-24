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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientUsername = :recipient")
    suspend fun markAllAsRead(recipient: String)
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM follows WHERE followerUsername = :follower AND followingUsername = :following LIMIT 1")
    suspend fun getFollowRecord(follower: String, following: String): Follow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: Follow)

    @Delete
    suspend fun deleteFollow(follow: Follow)

    @Query("SELECT followingUsername FROM follows WHERE followerUsername = :follower")
    fun getFollowingList(follower: String): Flow<List<String>>
}

@Dao
interface SavedPostDao {
    @Query("SELECT * FROM saved_posts WHERE username = :username AND postId = :postId LIMIT 1")
    suspend fun getSavedPost(username: String, postId: Int): SavedPost?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: Like)

    @Delete
    suspend fun deleteLike(like: Like)

    @Query("SELECT COUNT(*) FROM likes WHERE postId = :postId")
    fun getLikesCountForPost(postId: Int): Flow<Int>
}
