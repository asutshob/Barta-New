package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

class FollowRepository(private val context: Context) {
    private val db = BartaDatabase.getDatabase(context)
    private val followDao = db.followDao()
    private val userDao = db.userDao()
    private val notificationDao = db.notificationDao()

    suspend fun followUser(currentUsername: String, targetUsername: String) {
        if (currentUsername == targetUsername) return
        db.withTransaction {
            val record = followDao.getFollowRecord(currentUsername, targetUsername)
            if (record == null) {
                followDao.insertFollow(
                    Follow(
                        followerUsername = currentUsername,
                        followingUsername = targetUsername
                    )
                )
                
                // Update counter in user tables
                val currentUser = userDao.getUserByUsername(currentUsername)
                if (currentUser != null) {
                    userDao.updateUser(currentUser.copy(followingCount = currentUser.followingCount + 1))
                }
                
                val targetUser = userDao.getUserByUsername(targetUsername)
                if (targetUser != null) {
                    userDao.updateUser(targetUser.copy(followersCount = targetUser.followersCount + 1))
                }
                
                // Create follow notification
                val senderProfilePic = currentUser?.profilePicture ?: ""
                notificationDao.insertNotification(
                    Notification(
                        recipientUsername = targetUsername,
                        senderUsername = currentUsername,
                        senderProfilePicture = senderProfilePic,
                        type = "FOLLOW",
                        text = "আপনাকে অনুসরণ করা শুরু করেছেন।" // Bangladesh-local message: "Started following you."
                    )
                )
            }
        }
    }

    suspend fun unfollowUser(currentUsername: String, targetUsername: String) {
        db.withTransaction {
            val record = followDao.getFollowRecord(currentUsername, targetUsername)
            if (record != null) {
                followDao.deleteFollow(record)
                
                // Update counter in user tables
                val currentUser = userDao.getUserByUsername(currentUsername)
                if (currentUser != null) {
                    userDao.updateUser(currentUser.copy(followingCount = (currentUser.followingCount - 1).coerceAtLeast(0)))
                }
                
                val targetUser = userDao.getUserByUsername(targetUsername)
                if (targetUser != null) {
                    userDao.updateUser(targetUser.copy(followersCount = (targetUser.followersCount - 1).coerceAtLeast(0)))
                }
            }
        }
    }

    fun isFollowing(follower: String, following: String): Flow<Boolean> {
        return followDao.observeIsFollowing(follower, following)
    }

    fun getFollowers(username: String): Flow<List<User>> {
        return followDao.getFollowersForUser(username)
    }

    fun getFollowing(username: String): Flow<List<User>> {
        return followDao.getFollowingForUser(username)
    }

    fun getFollowersCount(username: String): Flow<Int> {
        return userDao.getFollowersCount(username)
    }

    fun getFollowingCount(username: String): Flow<Int> {
        return userDao.getFollowingCount(username)
    }

    suspend fun removeFollower(currentUsername: String, followerUsername: String) {
        db.withTransaction {
            val record = followDao.getFollowRecord(followerUsername, currentUsername)
            if (record != null) {
                followDao.deleteFollow(record)
                val currentUser = userDao.getUserByUsername(currentUsername)
                if (currentUser != null) {
                    userDao.updateUser(currentUser.copy(followersCount = (currentUser.followersCount - 1).coerceAtLeast(0)))
                }
                val followerUser = userDao.getUserByUsername(followerUsername)
                if (followerUser != null) {
                    userDao.updateUser(followerUser.copy(followingCount = (followerUser.followingCount - 1).coerceAtLeast(0)))
                }
            }
        }
    }
}
