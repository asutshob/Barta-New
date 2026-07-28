package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Post::class,
        Comment::class,
        Story::class,
        Notification::class,
        Follow::class,
        SavedPost::class,
        Like::class,
        Report::class,
        Block::class,
        Mute::class,
        CommentLike::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BartaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun storyDao(): StoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun followDao(): FollowDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun likeDao(): LikeDao
    abstract fun reportDao(): ReportDao
    abstract fun blockDao(): BlockDao
    abstract fun muteDao(): MuteDao
    abstract fun commentLikeDao(): CommentLikeDao

    companion object {
        @Volatile
        private var INSTANCE: BartaDatabase? = null

        fun getDatabase(context: Context): BartaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BartaDatabase::class.java,
                    "barta_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
