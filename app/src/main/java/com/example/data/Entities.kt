package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String, // e.g. "asutshob"
    val fullName: String,
    val dob: String,
    val profilePicture: String, // Base64 string or URI
    val bio: String,
    val phoneNumber: String,
    val password: String,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val aboutSection: String = "",
    val headerBanner: String = "", // Optional profile banner
    val isPrivate: Boolean = false,
    val isRestricted: Boolean = false
)

@Entity(tableName = "posts")
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val userFullName: String,
    val userProfilePicture: String,
    val caption: String,
    val location: String,
    val mediaPaths: String, // Comma/colon separated Base64 or local URIs (up to 4 photos)
    val isVideo: Boolean = false,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val repostCount: Int = 0,
    val viewCount: Int = 120,
    val isRepost: Boolean = false,
    val repostedByUsername: String? = null,
    val repostedByUserFullName: String? = null,
    val originalPostId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val postId: Int,
    val username: String,
    val userProfilePicture: String,
    val commentText: String,
    val parentCommentId: Int? = null, // For replies
    val replyToUsername: String? = null,
    val likeCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val userProfilePicture: String,
    val mediaPath: String, // Base64 or local URI
    val isVideo: Boolean = false,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientUsername: String,
    val senderUsername: String,
    val senderProfilePicture: String,
    val type: String, // "LIKE", "COMMENT", "FOLLOW", "REPLY", "SYSTEM"
    val postId: Int? = null,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "follows")
data class Follow(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val followerUsername: String,
    val followingUsername: String
)

@Entity(tableName = "saved_posts")
data class SavedPost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val postId: Int
)

@Entity(tableName = "likes")
data class Like(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val postId: Int
)

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reporterUsername: String,
    val targetType: String, // "POST" or "PROFILE"
    val targetId: String, // postId as string or targetUsername
    val reason: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocks")
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val blockerUsername: String,
    val blockedUsername: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "mutes")
data class Mute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val muterUsername: String,
    val mutedUsername: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comment_likes")
data class CommentLike(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val commentId: Int
)
