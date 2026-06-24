package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.InstaOrange
import com.example.ui.theme.InstaPink
import com.example.ui.theme.InstaPurple
import com.example.ui.theme.InstaYellow
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Language
import com.example.ui.viewmodel.BartaViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: BartaViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val strings = AppStrings(currentLanguage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Language Toggle Float Button
        IconButton(
            onClick = { viewModel.updateLanguage(if (currentLanguage == Language.BANGLA) Language.ENGLISH else Language.BANGLA) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Change Language",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        when (val screen = currentScreen) {
            is Screen.Login -> LoginContent(viewModel, strings)
            is Screen.SignupStep1 -> SignupStep1Content(viewModel, strings)
            is Screen.SignupStep2 -> SignupStep2Content(viewModel, strings)
            is Screen.Welcome -> WelcomeContent(viewModel, screen.username, strings)
            else -> {}
        }
    }
}

@Composable
fun LoginContent(viewModel: BartaViewModel, strings: AppStrings) {
    var phoneOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barta Logo Gradient Style
        Text(
            text = strings.appName,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                brush = Brush.linearGradient(
                    colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                )
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Login Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.login,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = phoneOrUsername,
                    onValueChange = { phoneOrUsername = it },
                    label = { Text(strings.phone + " / " + strings.username) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_phone_input")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.password) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (phoneOrUsername.isBlank() || password.isBlank()) {
                            errorMessage = strings.errorFillAll
                        } else {
                            coroutineScope.launch {
                                val success = viewModel.login(phoneOrUsername, password)
                                if (!success) {
                                    errorMessage = strings.errorLoginFailed
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.login, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.dontHaveAccount,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { viewModel.navigateTo(Screen.SignupStep1) }
                .padding(8.dp)
        )
    }
}

// Temporary Signup cache
private var tempFullName = ""
private var tempDob = ""
private var tempProfilePic: Bitmap? = null

@Composable
fun SignupStep1Content(viewModel: BartaViewModel, strings: AppStrings) {
    var fullName by remember { mutableStateOf(tempFullName) }
    var dob by remember { mutableStateOf(tempDob) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(tempProfilePic) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                // Pre-compress and scale to standard square size for social avatars
                val scaled = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
                imageBitmap = scaled
                tempProfilePic = scaled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.appName,
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                brush = Brush.linearGradient(
                    colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                )
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.step1,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Profile Picture Circle Selector
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .testTag("signup_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!.asImageBitmap(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = strings.changeProfilePicture,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(strings.fullName) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_name_input")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text(strings.dob) },
                    placeholder = { Text("DD/MM/YYYY") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_dob_input")
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (fullName.isBlank() || dob.isBlank()) {
                            errorMessage = strings.errorFillAll
                        } else {
                            tempFullName = fullName
                            tempDob = dob
                            viewModel.navigateTo(Screen.SignupStep2)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_step1_next"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(strings.next, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.alreadyHaveAccount,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { viewModel.navigateTo(Screen.Login) }
                .padding(8.dp)
        )
    }
}

@Composable
fun SignupStep2Content(viewModel: BartaViewModel, strings: AppStrings) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = { viewModel.navigateTo(Screen.SignupStep1) },
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Text(
            text = strings.appName,
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                brush = Brush.linearGradient(
                    colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                )
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.step2,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.username) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_username_input")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phone) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_phone_input")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.password) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(strings.bio) },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_bio_input")
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                }

                Button(
                    onClick = {
                        val cleanUsername = username.trim().lowercase().removePrefix("@")
                        if (phone.isBlank() || password.isBlank() || cleanUsername.isBlank() || bio.isBlank()) {
                            errorMessage = strings.errorFillAll
                        } else {
                            coroutineScope.launch {
                                isUploading = true
                                val success = viewModel.signup(
                                    username = cleanUsername,
                                    fullName = tempFullName,
                                    dob = tempDob,
                                    profilePic = tempProfilePic,
                                    bio = bio,
                                    phone = phone,
                                    pass = password
                                )
                                isUploading = false
                                if (!success) {
                                    errorMessage = strings.errorUsernameExists
                                } else {
                                    // Reset temp sign up
                                    tempFullName = ""
                                    tempDob = ""
                                    tempProfilePic = null
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isUploading
                ) {
                    Text(strings.signup, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WelcomeContent(viewModel: BartaViewModel, username: String, strings: AppStrings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.welcomeTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Custom colorful greeting box
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(InstaPurple, InstaPink, InstaOrange, InstaYellow)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 72.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "@$username",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = strings.welcomeDesc,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        )

        Button(
            onClick = { viewModel.navigateTo(Screen.Main) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("welcome_get_started"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(strings.getStarted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
