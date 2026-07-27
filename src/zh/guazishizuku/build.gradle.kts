import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Guazi (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "瓜子漫画(雫)"
        baseUrl = "https://www.guazimanhua.com"
        lang = "zh"
    }

    deeplink {
        path("/..*")
    }
}
