import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manwa (Shizuku)"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        name = "漫蛙(雫)"

        baseUrl {
            custom(
                "https://manwari.cc",
            )
        }
    }

    deeplink {
        path("/..*")
    }
}
