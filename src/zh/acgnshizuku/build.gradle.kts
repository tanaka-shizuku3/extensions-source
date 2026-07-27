import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "acgn (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "動漫戲說(雫)"
        baseUrl = "https://comic.acgn.cc"
        lang = "zh"
    }

    deeplink {
        path("/..*")
    }
}
