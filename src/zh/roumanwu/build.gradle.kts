import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 25
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz3.xyz/dizhi (自动更新)
        baseUrl = "https://roum29.xyz"
    }
}
