import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hipmh (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "嬉皮漫畫(雫)"
        baseUrl = "https://m.hipmh.com"
        lang = "zh"
    }

    deeplink {
        path("/works/..*")
    }
}
