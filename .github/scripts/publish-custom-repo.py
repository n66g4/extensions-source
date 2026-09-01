"""Publish custom extensions to n66g4/extensions repo/custom/."""

import gzip
import hashlib
import json
import os
import sys
import time
from pathlib import Path

import index_pb2
from google.protobuf import json_format

REPO_NAME = "n66g4/extensions"
RELEASE_BASE_URL = f"https://github.com/{REPO_NAME}/releases/download"
ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/n66g4/extensions-source@main"
SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"
ARTIFACTS_DIR = Path.home() / "apk-artifacts"
CUSTOM_DIR = Path.cwd() / "custom"


def run_gh(*args: str) -> str:
    import subprocess

    result = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


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
    tag = sha[:7]
    signing_key = os.environ["SIGNING_KEY_FINGERPRINT"]

    extensions: list[index_pb2.Extension] = []
    uploads: list[Path] = []

    for info_file in ARTIFACTS_DIR.glob("**/keiyoushi-source-info.json"):
        with info_file.open(encoding="utf-8") as f:
            info = json.load(f)
        apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
        jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)
        if jar is None:
            raise FileNotFoundError(f"No jar for {info['packageName']}")

        ext = index_pb2.Extension(
            name=info["name"],
            packageName=info["packageName"],
            resources=index_pb2.Resources(
                iconUrl=get_icon_url(info["module"], info.get("theme")),
                apkUrl=f"{RELEASE_BASE_URL}/{tag}/{apk.name}" if apk else "",
                jarUrl=f"{RELEASE_BASE_URL}/{tag}/{jar.name}",
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
        if apk:
            uploads.append(apk)
        uploads.append(jar)

    if not extensions:
        print("No extensions built")
        sys.exit(0)

    extensions.sort(key=lambda e: e.packageName)
    index = index_pb2.Index(
        name="n66g4 Custom",
        badgeLabel="N66",
        signingKey=signing_key,
        contact=index_pb2.Contact(website="https://github.com/n66g4/extensions"),
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

    # Upload release assets
    try:
        run_gh("release", "view", tag, "--repo", REPO_NAME)
    except RuntimeError:
        run_gh(
            "release", "create", tag,
            "--repo", REPO_NAME,
            "--title", f"Custom extensions {tag}",
            "--notes", f"Built from n66g4/extensions-source@{sha}",
        )
        time.sleep(3)

    run_gh("release", "upload", tag, *[str(f) for f in uploads], "--repo", REPO_NAME, "--clobber")
    print(f"Published {len(extensions)} extension(s) to {REPO_NAME}/custom/")


if __name__ == "__main__":
    main()
