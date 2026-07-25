import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebDAV"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "WebDAV"
        lang = "all"
        baseUrl {
            custom("https://127.0.0.1")
        }
    }

    source {
        name = "WebDAV (2)"
        lang = "all"
        baseUrl {
            custom("https://127.0.0.1")
        }
    }
}
