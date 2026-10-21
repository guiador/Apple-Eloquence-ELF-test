"""Portable equivalent of verify.sh: verify the vendored files, without converting them."""
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def main():
    count = 0
    for line in (ROOT/"tools/checksums.txt").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\*?(.+)", line)
        if match is None:
            raise ValueError("Invalid checksum entry")
        expected, name = match.groups()
        path = (ROOT/name).resolve()
        if ROOT not in path.parents:
            raise ValueError("Checksum path outside the project")
        actual = hashlib.sha256()
        with path.open("rb") as source:
            for block in iter(lambda: source.read(1024*1024), b""):
                actual.update(block)
        if actual.hexdigest() != expected.lower():
            raise ValueError(f"Checksum mismatch: {name}")
        count += 1
    if count == 0:
        raise ValueError("No checksums found")
    print(f"PASS {count} vendored-file SHA-256 checks; no synthesis or Android device validation.")


if __name__ == "__main__":
    main()
