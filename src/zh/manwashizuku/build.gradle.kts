import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manwa (Shizuku)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        name = "漫蛙(雫)"

        baseUrl {
            mirrors(
                "https://manwali.cc",
                "https://mwuu.cc",
                "https://www.manwayi.cc",
            )
        }
    }

    deeplink {
        path("/..*")
    }
}
