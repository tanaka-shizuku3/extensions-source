import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuamanga (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mccms"

    source {
        name = "漫画manga(雫)"
        lang = "zh"
        baseUrl = "https://www.mh03.com"
    }
}
