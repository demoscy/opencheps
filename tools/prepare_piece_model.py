#!/usr/bin/env python3
"""Build-time only: fetch pinned, SHA256-verified offline model and test fixtures.

No Python packages needed. Nothing is downloaded by the Android application.
"""
import argparse
import hashlib
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REVISION = "be5b491bc51b2e2a231b7616d9256748ee8ab5b9"
BASE = f"https://raw.githubusercontent.com/scoriiu/fenshot/{REVISION}/packages/fenshot/"
MODEL = ("chess-tiles-v2.onnx", "883f6a8e639e6d6b6399b3fda0508ad772e3c6f9cefa2e678a13f27b9fa6248d")
FIXTURES = {
    "chesscom-italian-white.png": "30804409987f645852e1e7f94e69e1cfd3e790b7daee3c5971ae77082da7f4f9",
    "lichess-italian-black.png": "949af1973717b177089822cb130941a211f792fb0f9e17fc4880c7545712a57d",
    "lichess-italian-horsey.png": "a1c44654d64cd5db949faea65dbf9db11137efef60a04e5f72c0c6fbdad86811",
    "example-gray.u8.bin": "619c00b9593eb29e9bc9195104c33542059b406c0f015f7be99eee43e80224e3",
}


def fetch(source, destination, digest):
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() == digest:
        return
    with urllib.request.urlopen(source if source.startswith("https://") else BASE + source, timeout=60) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if hashlib.sha256(data).hexdigest() != digest:
        raise SystemExit(f"SHA256 mismatch: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)
    print(f"Verified {destination.relative_to(ROOT)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tests", action="store_true", help="also fetch reference fixtures")
    args = parser.parse_args()
    fetch("model/" + MODEL[0], ROOT / "app/src/main/assets/piece-model" / MODEL[0], MODEL[1])
    fetch("https://raw.githubusercontent.com/microsoft/onnxruntime/v1.23.2/ThirdPartyNotices.txt",
          ROOT / "app/src/main/assets/piece-model/ThirdPartyNotices-onnxruntime.txt",
          "e9e90971a8e75a9a8ac0c6412e29c1202d079998389915aa485f46c816c3b4cc")
    if args.tests:
        for name, digest in FIXTURES.items():
            fetch("tests/fixtures/" + name, ROOT / "app/src/test/resources/piece-model" / name, digest)
