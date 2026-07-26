import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "tibiu (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mccms"

    source {
        name = "TIBIU(雫)"
        baseUrl = "https://comic.tibiu.net"
        lang = "zh"
    }

    deeplink {
        path("/..*")
    }
}
