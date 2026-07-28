package com.example.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Language {
    BANGLA,
    ENGLISH
}

object LocalizationManager {
    private val _currentLanguage = MutableStateFlow(Language.BANGLA)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()

    fun setLanguage(language: Language) {
        _currentLanguage.value = language
    }

    fun toggleLanguage() {
        _currentLanguage.value = if (_currentLanguage.value == Language.BANGLA) {
            Language.ENGLISH
        } else {
            Language.BANGLA
        }
    }
}

class AppStrings(private val lang: Language) {
    val appName: String
        get() = if (lang == Language.BANGLA) "বার্তা" else "Barta"

    val login: String
        get() = if (lang == Language.BANGLA) "লগইন" else "Login"

    val signup: String
        get() = if (lang == Language.BANGLA) "সাইন আপ" else "Sign Up"

    val phone: String
        get() = if (lang == Language.BANGLA) "বাংলাদেশি মোবাইল নম্বর" else "Bangladesh Mobile Number"

    val password: String
        get() = if (lang == Language.BANGLA) "পাসওয়ার্ড" else "Password"

    val fullName: String
        get() = if (lang == Language.BANGLA) "সম্পূর্ণ নাম" else "Full Name"

    val username: String
        get() = if (lang == Language.BANGLA) "ইউজার আইডি (@username)" else "User ID (@username)"

    val dob: String
        get() = if (lang == Language.BANGLA) "জন্ম তারিখ (দিন/মাস/বছর)" else "Date of Birth (DD/MM/YYYY)"

    val bio: String
        get() = if (lang == Language.BANGLA) "বায়ো (সংক্ষিপ্ত পরিচয়)" else "Bio (Short Intro)"

    val aboutSection: String
        get() = if (lang == Language.BANGLA) "আমার সম্পর্কে" else "About Me"

    val stories: String
        get() = if (lang == Language.BANGLA) "স্টোরি সমূহ" else "Stories"

    val home: String
        get() = if (lang == Language.BANGLA) "হোম" else "Home"

    val explore: String
        get() = if (lang == Language.BANGLA) "অনুসন্ধান" else "Explore"

    val reels: String
        get() = if (lang == Language.BANGLA) "রিলস" else "Reels"

    val profile: String
        get() = if (lang == Language.BANGLA) "প্রোফাইল" else "Profile"

    val notifications: String
        get() = if (lang == Language.BANGLA) "বিজ্ঞপ্তি সমূহ" else "Notifications"

    val addStory: String
        get() = if (lang == Language.BANGLA) "স্টোরি যোগ করুন" else "Add Story"

    val createPost: String
        get() = if (lang == Language.BANGLA) "পোস্ট তৈরি করুন" else "Create Post"

    val caption: String
        get() = if (lang == Language.BANGLA) "ক্যাপশন লিখুন..." else "Write a caption..."

    val location: String
        get() = if (lang == Language.BANGLA) "লোকেশন যোগ করুন" else "Add Location"

    val post: String
        get() = if (lang == Language.BANGLA) "পোস্ট করুন" else "Post"

    val search: String
        get() = if (lang == Language.BANGLA) "@username বা নাম দিয়ে খুঁজুন..." else "Search by @username or name..."

    val editProfile: String
        get() = if (lang == Language.BANGLA) "প্রোফাইল পরিবর্তন" else "Edit Profile"

    val followers: String
        get() = if (lang == Language.BANGLA) "অনুসারী" else "Followers"

    val following: String
        get() = if (lang == Language.BANGLA) "ফলোয়িং" else "Following"

    val follow: String
        get() = if (lang == Language.BANGLA) "ফলো করুন" else "Follow"

    val followed: String
        get() = if (lang == Language.BANGLA) "ফলো করা হয়েছে" else "Followed"

    val unfollow: String
        get() = if (lang == Language.BANGLA) "আনফলো করুন" else "Unfollow"

    val settings: String
        get() = if (lang == Language.BANGLA) "সেটিংস" else "Settings"

    val logout: String
        get() = if (lang == Language.BANGLA) "লগআউট" else "Logout"

    val welcomeTitle: String
        get() = if (lang == Language.BANGLA) "বার্তায় আপনাকে স্বাগতম!" else "Welcome to Barta!"

    val welcomeDesc: String
        get() = if (lang == Language.BANGLA) "বার্তা (Barta) এ সাইন আপ করার জন্য আপনাকে ধন্যবাদ। আপনার আনন্দময় মুহূর্তগুলো ছবি ও ছোট ভিডিওর মাধ্যমে প্রিয়জনদের সাথে শেয়ার করুন।" else "Thank you for signing up on Barta. Share your joyful moments with loved ones through photos and short videos."

    val getStarted: String
        get() = if (lang == Language.BANGLA) "অ্যাপে প্রবেশ করুন" else "Enter App"

    val step1: String
        get() = if (lang == Language.BANGLA) "ধাপ ১: ব্যক্তিগত তথ্য" else "Step 1: Personal Info"

    val step2: String
        get() = if (lang == Language.BANGLA) "ধাপ ২: অ্যাকাউন্ট তথ্য" else "Step 2: Account Info"

    val next: String
        get() = if (lang == Language.BANGLA) "পরবর্তী" else "Next"

    val back: String
        get() = if (lang == Language.BANGLA) "পূর্ববর্তী" else "Back"

    val alreadyHaveAccount: String
        get() = if (lang == Language.BANGLA) "ইতিমধ্যে অ্যাকাউন্ট আছে? লগইন করুন" else "Already have an account? Login"

    val dontHaveAccount: String
        get() = if (lang == Language.BANGLA) "অ্যাকাউন্ট নেই? সাইন আপ করুন" else "Don't have an account? Sign Up"

    val save: String
        get() = if (lang == Language.BANGLA) "সংরক্ষণ করুন" else "Save"

    val languageToggleLabel: String
        get() = if (lang == Language.BANGLA) "English এ পরিবর্তন করুন" else "Switch to বাংলা"

    val changeProfilePicture: String
        get() = if (lang == Language.BANGLA) "প্রোফাইল ছবি পরিবর্তন করুন" else "Change Profile Picture"

    val doubleTapTip: String
        get() = if (lang == Language.BANGLA) "লাইক করতে ডাবল ট্যাপ করুন" else "Double-tap to like"

    val commentPlaceholder: String
        get() = if (lang == Language.BANGLA) "একটি মন্তব্য লিখুন..." else "Add a comment..."

    val replyLabel: String
        get() = if (lang == Language.BANGLA) "উত্তর দিন" else "Reply"

    val commentsTitle: String
        get() = if (lang == Language.BANGLA) "মন্তব্য সমূহ" else "Comments"

    val emptyPosts: String
        get() = if (lang == Language.BANGLA) "কোনো পোস্ট নেই" else "No posts yet"

    val editPostCaption: String
        get() = if (lang == Language.BANGLA) "ক্যাপশন সংশোধন করুন" else "Edit Caption"

    val deletePostLabel: String
        get() = if (lang == Language.BANGLA) "পোস্ট মুছুন" else "Delete Post"

    val cancel: String
        get() = if (lang == Language.BANGLA) "বাতিল" else "Cancel"

    val progressUploading: String
        get() = if (lang == Language.BANGLA) "পোস্ট আপলোড হচ্ছে..." else "Uploading post..."

    val successPost: String
        get() = if (lang == Language.BANGLA) "পোস্টটি সফলভাবে আপলোড হয়েছে!" else "Post uploaded successfully!"

    val errorFillAll: String
        get() = if (lang == Language.BANGLA) "দয়া করে সব তথ্য পূরণ করুন" else "Please fill all fields"

    val errorUsernameExists: String
        get() = if (lang == Language.BANGLA) "এই ইউজার আইডিটি ইতিমধ্যে ব্যবহৃত" else "This username is already taken"

    val errorLoginFailed: String
        get() = if (lang == Language.BANGLA) "ভুল ফোন নম্বর বা পাসওয়ার্ড" else "Invalid phone number or password"

    val imageSelected: String
        get() = if (lang == Language.BANGLA) "মিডিয়া ফাইল সিলেক্ট করা হয়েছে" else "Media file selected"

    val selectMedia: String
        get() = if (lang == Language.BANGLA) "ছবি বা ভিডিও নির্বাচন করুন" else "Select Photo or Video"

    val taggedPosts: String
        get() = if (lang == Language.BANGLA) "ট্যাগ করা পোস্ট" else "Tagged"

    val myPosts: String
        get() = if (lang == Language.BANGLA) "আমার পোস্ট" else "My Posts"

    val savedPostsLabel: String
        get() = if (lang == Language.BANGLA) "সংরক্ষিত পোস্ট" else "Saved Posts"

    val followBack: String
        get() = if (lang == Language.BANGLA) "ফিরতি ফলো করুন" else "Follow Back"

    val followersList: String
        get() = if (lang == Language.BANGLA) "অনুসারী" else "Followers"

    val followingList: String
        get() = if (lang == Language.BANGLA) "ফলোয়িং" else "Following"

    val emptyFollowers: String
        get() = if (lang == Language.BANGLA) "কোনো অনুসারী নেই" else "No followers yet"

    val emptyFollowing: String
        get() = if (lang == Language.BANGLA) "কাউকে ফলো করছেন না" else "Not following anyone yet"

    val confirmUnfollow: String
        get() = if (lang == Language.BANGLA) "আপনি কি আনফলো করতে চান?" else "Do you want to unfollow?"

    val report: String
        get() = if (lang == Language.BANGLA) "রিপোর্ট করুন" else "Report"

    val reportPost: String
        get() = if (lang == Language.BANGLA) "পোস্ট রিপোর্ট করুন" else "Report Post"

    val reportProfile: String
        get() = if (lang == Language.BANGLA) "প্রোফাইল রিপোর্ট করুন" else "Report Profile"

    val reportReason: String
        get() = if (lang == Language.BANGLA) "রিপোর্টের কারণ নির্বাচন করুন" else "Select Report Reason"

    val reasonSpam: String
        get() = if (lang == Language.BANGLA) "স্প্যাম বা ভুয়া কনটেন্ট" else "Spam or Misleading"

    val reasonHarassment: String
        get() = if (lang == Language.BANGLA) "হয়রানি বা কটূক্তি" else "Harassment or Hate Speech"

    val reasonInappropriate: String
        get() = if (lang == Language.BANGLA) "অনুপযুক্ত বা আপত্তিকর কনটেন্ট" else "Inappropriate Content"

    val reasonFake: String
        get() = if (lang == Language.BANGLA) "নকল বা ভুয়া অ্যাকাউন্ট" else "Fake Account or Impersonation"

    val reasonOther: String
        get() = if (lang == Language.BANGLA) "অন্যান্য কারণ" else "Other Reason"

    val reportSubmitted: String
        get() = if (lang == Language.BANGLA) "আপনার রিপোর্ট জমা নেওয়া হয়েছে। ধন্যবাদ!" else "Report submitted. Thank you!"

    val block: String
        get() = if (lang == Language.BANGLA) "ব্লক করুন" else "Block"

    val unblock: String
        get() = if (lang == Language.BANGLA) "আনব্লক করুন" else "Unblock"

    val blockUserConfirm: String
        get() = if (lang == Language.BANGLA) "আপনি কি এই ইউজারকে ব্লক করতে চান? আপনারা একে অপরের পোস্ট দেখতে পাবেন না।" else "Are you sure you want to block this user? You will no longer see each other's posts."

    val mute: String
        get() = if (lang == Language.BANGLA) "মিউট করুন" else "Mute"

    val unmute: String
        get() = if (lang == Language.BANGLA) "আনমিউট করুন" else "Unmute"

    val removeFollower: String
        get() = if (lang == Language.BANGLA) "অনুসারী সরান" else "Remove Follower"

    val confirmRemoveFollower: String
        get() = if (lang == Language.BANGLA) "আপনি কি এই অনুসারীকে তালিকা থেকে সরাতে চান?" else "Remove this follower from your list?"

    val privateAccount: String
        get() = if (lang == Language.BANGLA) "প্রাইভেট অ্যাকাউন্ট" else "Private Account"

    val privateAccountDesc: String
        get() = if (lang == Language.BANGLA) "শুধুমাত্র আপনার অনুসারীরা আপনার পোস্ট দেখতে পাবে" else "Only your followers can see your posts"

    val restrictAccount: String
        get() = if (lang == Language.BANGLA) "অ্যাকাউন্ট রেস্ট্রিক্ট" else "Restrict Account"

    val clearAll: String
        get() = if (lang == Language.BANGLA) "সব মুছুন" else "Clear All"

    val markAllRead: String
        get() = if (lang == Language.BANGLA) "সব পড়া চিহ্নিত করুন" else "Mark All Read"

    val notificationsEmpty: String
        get() = if (lang == Language.BANGLA) "কোনো নতুন বিজ্ঞপ্তি নেই" else "No new notifications"

    val share: String
        get() = if (lang == Language.BANGLA) "শেয়ার করুন" else "Share"

    val copyLink: String
        get() = if (lang == Language.BANGLA) "লিংক কপি করুন" else "Copy Link"

    val linkCopied: String
        get() = if (lang == Language.BANGLA) "লিংক ক্লিপবোর্ডে কপি করা হয়েছে!" else "Link copied to clipboard!"

    val views: String
        get() = if (lang == Language.BANGLA) "বার দেখা হয়েছে" else "views"

    val suggestedForYou: String
        get() = if (lang == Language.BANGLA) "আপনার জন্য প্রস্তাবিত" else "Suggested for You"

    val trendingHashtags: String
        get() = if (lang == Language.BANGLA) "জনপ্রিয় হ্যাশট্যাগ" else "Trending Hashtags"

    val submit: String
        get() = if (lang == Language.BANGLA) "জমা দিন" else "Submit"
}
