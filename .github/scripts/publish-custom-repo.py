"""Publish custom extensions to n66g4/extensions repo/custom/."""

import gzip
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

import index_pb2
from google.protobuf import json_format

REPO_NAME = "n66g4/extensions"
RAW_BASE_URL = f"https://github.com/{REPO_NAME}/raw/repo/custom"
ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/n66g4/extensions-source@main"
SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"
ARTIFACTS_DIR = Path.home() / "apk-artifacts"
CUSTOM_DIR = Path.cwd() / "custom"
JARS_DIR = CUSTOM_DIR / "jars"


def get_icon_url(module: str, theme: str | None) -> str:
    module_icon = f"src/{module.replace('.', '/')}/{ICON_FILE}"
    if (SOURCE_DIR / module_icon).exists():
        return f"{ICON_BASE_URL}/{module_icon}"
    if theme:
        theme_icon = f"lib-multisrc/{theme}/{ICON_FILE}"
        if (SOURCE_DIR / theme_icon).exists():
            return f"{ICON_BASE_URL}/{theme_icon}"
    return f"{ICON_BASE_URL}/core/src/main/{ICON_FILE}"


def main() -> None:
    sha = sys.argv[1]
    signing_key = os.environ["SIGNING_KEY_FINGERPRINT"]

    extensions: list[index_pb2.Extension] = []
    JARS_DIR.mkdir(parents=True, exist_ok=True)

    for info_file in ARTIFACTS_DIR.glob("**/keiyoushi-source-info.json"):
        with info_file.open(encoding="utf-8") as f:
            info = json.load(f)
        apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
        jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)
        if jar is None:
            raise FileNotFoundError(f"No jar for {info['packageName']}")

        shutil.copy2(jar, JARS_DIR / jar.name)
        jar_url = f"{RAW_BASE_URL}/jars/{jar.name}"
        apk_url = f"{RAW_BASE_URL}/jars/{apk.name}" if apk else ""

        ext = index_pb2.Extension(
            name=info["name"],
            packageName=info["packageName"],
            resources=index_pb2.Resources(
                iconUrl=get_icon_url(info["module"], info.get("theme")),
                apkUrl=apk_url,
                jarUrl=jar_url,
            ),
            extensionLib=info["extensionLib"],
            versionCode=info["versionCode"],
            versionName=info["versionName"],
            contentWarning=info["contentWarning"],
            sources=[
                index_pb2.Source(
                    id=int(s["id"]),
                    name=s["name"],
                    language=s["lang"],
                    homeUrl=s["baseUrl"],
                    mirrorUrls=s.get("mirrorUrls", []),
                )
                for s in info["sources"]
            ],
        )
        extensions.append(ext)

    if not extensions:
        print("No extensions built")
        sys.exit(0)

    extensions.sort(key=lambda e: e.packageName)
    index = index_pb2.Index(
        name="n66g4 Custom",
        badgeLabel="N66",
        signingKey=signing_key,
        contact=index_pb2.Contact(website=f"https://github.com/{REPO_NAME}"),
        extensionList=index_pb2.ExtensionList(extensions=extensions),
    )

    CUSTOM_DIR.mkdir(parents=True, exist_ok=True)
    (CUSTOM_DIR / "index.json").write_text(
        json_format.MessageToJson(
            index,
            always_print_fields_with_no_presence=False,
            preserving_proto_field_name=True,
        ),
        encoding="utf-8",
    )
    (CUSTOM_DIR / "index.pb").write_bytes(
        gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    )
    (CUSTOM_DIR / "repo.json").write_text(
        json.dumps(
            {
                "index_v2": f"{RAW_BASE_URL}/index.pb",
                "meta": {
                    "name": "n66g4 Custom",
                    "website": f"https://github.com/{REPO_NAME}",
                    "signingKeyFingerprint": signing_key,
                },
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Published {len(extensions)} extension(s) to {REPO_NAME}/custom/")


if __name__ == "__main__":
    main()
