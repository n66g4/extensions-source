import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 28
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz4.xyz/dizhi (自动更新)
        baseUrl = "https://roum29.xyz"
    }

    deeplink {
        host("rouman5.com")
        host("roum29.xyz")
        path("/books/..*")
    }
}
