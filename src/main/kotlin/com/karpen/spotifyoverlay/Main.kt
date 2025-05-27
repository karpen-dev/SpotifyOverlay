package com.karpen.spotifyoverlay

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.MenuItem
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.classic.methods.HttpUriRequest
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.StringEntity
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpotifyOverlay : Application() {
    private val logger = LoggerFactory.getLogger(SpotifyOverlay::class.java)

    private val clientId = "PASTE THIS YOU SPOTIFY APP CLIENT ID"
    private val clientSecret = "PASTE THIS YOU SPOTIFY APP CLIENT SECRET"
    private val redirectUri = "http://127.0.0.1:8888/callback" // You can put this any url, but it must match the redirect url in the dashboard
    private val scope = "user-read-currently-playing user-modify-playback-state"

    private var accessToken = ""
    private var refreshToken = ""
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    private lateinit var albumArt: ImageView
    private lateinit var trackName: Text
    private lateinit var artistName: Text
    private lateinit var playPauseBtn: Button
    private lateinit var defaultAlbumImage: Image

    private val apiExecutor = Executors.newCachedThreadPool()
    private val httpClient = HttpClients.createDefault()

    override fun init() {
        try {
            logger.info("Initializing application...")
            val imageStream: InputStream? = javaClass.getResourceAsStream("/images/default_album.png")
            defaultAlbumImage = if (imageStream != null) {
                Image(imageStream).also {
                    logger.debug("Default album image loaded from resources")
                }
            } else {
                logger.warn("Default album image not found in resources, using placeholder")
                Image("https://via.placeholder.com/50?text=No+Image", 50.0, 50.0, true, true)
            }
        } catch (e: Exception) {
            logger.error("Error loading default album image", e)
        }
    }

    override fun start(primaryStage: Stage) {
        try {
            logger.info("Starting application...")
            setupUI(primaryStage)

            if (loadTokens()) {
                logger.info("Using saved tokens")
                Platform.runLater {
                    trackName.text = "Connected!"
                    artistName.text = "Ready to control Spotify"
                }
                startTrackUpdates()
            } else {
                authenticate()
                startTrackUpdates()
            }
        } catch (e: Exception) {
            logger.error("Application startup failed", e)
            Platform.exit()
        }
    }

    private fun setupUI(stage: Stage) {
        albumArt = ImageView(defaultAlbumImage).apply {
            fitWidth = 50.0
            fitHeight = 50.0
            isPreserveRatio = true
            this.isMouseTransparent = true
        }

        trackName = Text("Connecting to Spotify...").apply {
            fill = Color.WHITE
            style = "-fx-font-weight: bold;"
            this.isMouseTransparent = true
        }

        artistName = Text("").apply {
            fill = Color.LIGHTGRAY
            this.isMouseTransparent = true
        }

        val prevBtn = createControlButton("⏮") { controlPlayback("previous") }.apply {
            this.isMouseTransparent = false
        }
        playPauseBtn = createControlButton("▶") { controlPlayback("toggle") }.apply {
            this.isMouseTransparent = false
        }
        val nextBtn = createControlButton("⏭") { controlPlayback("next") }.apply {
            this.isMouseTransparent = false
        }

        val content = createLayout(prevBtn, playPauseBtn, nextBtn).apply {
            this.isMouseTransparent = false
        }

        setupSceneAndStage(stage, content)
    }

    private fun createControlButton(text: String, action: () -> Unit): Button {
        return Button(text).apply {
            style = "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px;"
            setOnAction { action() }
        }
    }

    private fun createLayout(vararg buttons: Button): HBox {
        val trackInfo = VBox(2.0, trackName, artistName)
        val controls = HBox(5.0, *buttons)
        return HBox(10.0, albumArt, VBox(5.0, trackInfo, controls)).apply {
            padding = Insets(10.0)
            background = Background(BackgroundFill(
                Color.rgb(40, 40, 40, 0.8),
                CornerRadii(5.0),
                Insets.EMPTY
            ))
            border = Border(BorderStroke(
                Color.rgb(255, 255, 255, 0.2),
                BorderStrokeStyle.SOLID,
                CornerRadii(5.0),
                BorderWidths(1.0)
            ))
        }
    }

    private fun setupSceneAndStage(stage: Stage, content: HBox) {
        val scene = Scene(content, 300.0, 80.0, Color.TRANSPARENT).apply {
            setOnMousePressed { event ->
                stage.x = event.screenX - event.sceneX
                stage.y = event.screenY - event.sceneY
            }
            setOnMouseDragged { event ->
                stage.x = event.screenX - event.sceneX
                stage.y = event.screenY - event.sceneY
            }
        }

        stage.apply {
            title = "Spotify Overlay"

            initStyle(StageStyle.UTILITY)
            initStyle(StageStyle.TRANSPARENT)

            this.scene = scene
            isAlwaysOnTop = true
            x = javafx.stage.Screen.getPrimary().visualBounds.width - 320
            y = 10.0
            show()
        }
    }

    private fun authenticate() {
        Thread {
            try {
                logger.debug("Starting authentication process")
                val authUrl = buildAuthUrl()
                Platform.runLater { hostServices.showDocument(authUrl) }

                val code = showAuthDialog() ?: throw Exception("Authorization cancelled")
                logger.debug("Received authorization code")

                val tokens = exchangeCodeForTokens(code)
                accessToken = tokens["access_token"] ?: ""
                refreshToken = tokens["refresh_token"] ?: ""
                saveTokens()

                logger.info("Successfully authenticated with Spotify")
                Platform.runLater {
                    trackName.text = "Connected!"
                    artistName.text = "Ready to control Spotify"
                }
            } catch (e: Exception) {
                logger.error("Authentication failed", e)
                Platform.runLater {
                    trackName.text = "Auth Error"
                    artistName.text = e.message?.take(30) + "..."
                }
            }
        }.start()
    }

    private fun buildAuthUrl(): String {
        return "https://accounts.spotify.com/authorize?" +
                "response_type=code" +
                "&client_id=$clientId" +
                "&scope=${scope.replace(" ", "%20")}" +
                "&redirect_uri=$redirectUri"
    }

    private fun showAuthDialog(): String? {
        val result = arrayOf<String?>(null)
        Platform.runLater {
            result[0] = javafx.scene.control.TextInputDialog().apply {
                title = "Spotify Authorization"
                headerText = "Please enter the authorization code from Spotify:"
                contentText = "After authorizing, paste the code here:"
            }.showAndWait().orElse(null)
        }
        while (result[0] == null) Thread.sleep(100)
        return result[0]
    }

    private fun exchangeCodeForTokens(code: String): Map<String, String?> {
        HttpClients.createDefault().use { client ->
            val post = HttpPost("https://accounts.spotify.com/api/token")
            post.setHeader("Content-Type", "application/x-www-form-urlencoded")
            post.entity = StringEntity(
                "grant_type=authorization_code" +
                        "&code=$code" +
                        "&redirect_uri=$redirectUri" +
                        "&client_id=$clientId" +
                        "&client_secret=$clientSecret"
            )

            val response = client.execute(post) { response ->
                response.entity.content.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(response)
            logger.debug("Token exchange response: $json")
            return mapOf(
                "access_token" to json.optString("access_token"),
                "refresh_token" to json.optString("refresh_token")
            )
        }
    }

    private fun saveTokens() {
        try {
            val json = JSONObject()
            json.put("access_token", accessToken)
            json.put("refresh_token", refreshToken)
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get("tokens.json"),
                json.toString()
            )
            logger.info("Tokens saved successfully.")
        } catch (e: Exception) {
            logger.error("Error saving tokens", e)
        }
    }

    private fun loadTokens(): Boolean {
        try {
            val path = java.nio.file.Paths.get("tokens.json")
            if (!java.nio.file.Files.exists(path)) {
                return false
            }

            val json = JSONObject(java.nio.file.Files.readString(path))
            accessToken = json.optString("access_token", "")
            refreshToken = json.optString("refresh_token", "")
            logger.info("Tokens loaded successfully.")
            return true
        } catch (e: Exception) {
            logger.error("Error loading tokens", e)
            return false
        }
    }

    private fun startTrackUpdates() {
        scheduler.scheduleAtFixedRate({
            try {
                if (accessToken.isNotEmpty()) {
                    val track = getCurrentTrack()
                    Platform.runLater { updateUI(track) }
                }
            } catch (e: Exception) {
                logger.error("Error updating track info", e)
                Platform.runLater {
                    trackName.text = "Update Error"
                    artistName.text = e.message?.take(30) + "..."
                }
            }
        }, 0, 2, TimeUnit.SECONDS)
    }

    private fun getCurrentTrack(): SpotifyTrack {
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(10, TimeUnit.SECONDS)
            .setResponseTimeout(10, TimeUnit.SECONDS)
            .build()

        return try {
            HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()
                .use { client ->
                    val get = HttpGet("https://api.spotify.com/v1/me/player/currently-playing")
                    get.setHeader("Authorization", "Bearer $accessToken")

                    client.execute(get).use { response ->
                        when (response.code) {
                            200 -> {
                                val json = response.entity?.content?.bufferedReader()?.use { it.readText() }
                                    ?: return@use SpotifyTrack("Error", "Empty response", "", false)
                                parseTrackInfo(json)
                            }
                            204 -> SpotifyTrack("Not Playing", "", "", false)
                            401 -> SpotifyTrack("Auth Error", "Invalid or expired token", "", false)
                            403 -> SpotifyTrack("Auth Error", "Insufficient permissions", "", false)
                            429 -> SpotifyTrack("Error", "Rate limit exceeded", "", false)
                            else -> {
                                val errorBody = response.entity?.content?.bufferedReader()?.use { it.readText() }
                                    ?: "No error details"
                                SpotifyTrack("API Error", "Status: ${response.code}, $errorBody", "", false)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            SpotifyTrack("Network Error", e.message ?: "Unknown error", "", false)
        }
    }

    private fun parseTrackInfo(json: String): SpotifyTrack {
        try {
            val obj = JSONObject(json)
            val isPlaying = obj.getBoolean("is_playing")
            val item = obj.getJSONObject("item")
            val name = item.getString("name")
            val artists = item.getJSONArray("artists")
            val artistName = artists.getJSONObject(0).getString("name")

            val album = item.getJSONObject("album")
            val images = album.getJSONArray("images")
            val imageUrl = if (images.length() > 0) {
                images.getJSONObject(0).getString("url")
            } else ""

            return SpotifyTrack(name, artistName, imageUrl, isPlaying)
        } catch (e: Exception) {
            logger.error("Error parsing track info", e)
            return SpotifyTrack("Error", "Could not parse track info", "", false)
        }
    }

    private fun controlPlayback(action: String) {
        apiExecutor.submit {
            try {
                if (accessToken.isEmpty()) {
                    Platform.runLater {
                        trackName.text = "Not Authenticated"
                        artistName.text = "Please login first"
                    }
                    return@submit
                }

                val (endpoint, method) = when (action) {
                    "previous" -> "previous" to HttpPost::class.java
                    "next" -> "next" to HttpPost::class.java
                    "toggle" -> (if (playPauseBtn.text == "⏸") "pause" else "play") to HttpPut::class.java
                    else -> return@submit
                }

                logger.debug("Sending $endpoint command to Spotify API")

                val request = when (method) {
                    HttpPost::class.java -> HttpPost("https://api.spotify.com/v1/me/player/$endpoint")
                    HttpPut::class.java -> HttpPut("https://api.spotify.com/v1/me/player/$endpoint")
                    else -> return@submit
                }

                request.apply {
                    setHeader("Authorization", "Bearer $accessToken")
                    setHeader("Content-Type", "application/json")
                }

                httpClient.execute(request) { response ->
                    val result = when (response.code) {
                        200 or 204 -> {
                            logger.debug("Command $endpoint succeeded")
                            if (action == "toggle") {
                                Platform.runLater {
                                    playPauseBtn.text = if (endpoint == "pause") "▶" else "⏸"
                                }
                            }
                            "Success"
                        }
                        else -> "Error: ${response.code}"
                    }

                    if (result != "Success") {
                        Platform.runLater {
                            artistName.text = result
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Playback control failed", e)
                Platform.runLater {
                    artistName.text = "Control error: ${e.message?.take(30)}..."
                }
            }
        }
    }

    private fun updateUI(track: SpotifyTrack) {
        Platform.runLater {
            trackName.text = track.name
            artistName.text = track.artist
            playPauseBtn.text = if (track.isPlaying) "⏸" else "▶"

            try {
                albumArt.image = if (track.imageUrl.isNotEmpty()) {
                    try {
                        Image(track.imageUrl, 50.0, 50.0, true, true)
                    } catch (e: Exception) {
                        logger.warn("Error loading track image from URL", e)
                        defaultAlbumImage
                    }
                } else {
                    defaultAlbumImage
                }
            } catch (e: Exception) {
                logger.error("Error updating album art", e)
                albumArt.image = defaultAlbumImage
            }
        }
    }

    override fun stop() {
        logger.info("Shutting down application...")
        scheduler.shutdownNow()
        apiExecutor.shutdownNow()
        httpClient.close()
    }

    data class SpotifyTrack(
        val name: String,
        val artist: String,
        val imageUrl: String,
        val isPlaying: Boolean
    )
}

fun main() {
    Application.launch(SpotifyOverlay::class.java)
}