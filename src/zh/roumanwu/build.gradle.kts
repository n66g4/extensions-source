import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Roumanwu"
    versionCode = 22
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "肉漫屋"
        lang = "zh"

        // 地址: https://rou.pub/dizhi or https://rdz3.xyz/dizhi
        baseUrl {
            mirrors(
                "https://rouman5.com",
                "https://roum2.xyz",
            )
        }
    }
}
