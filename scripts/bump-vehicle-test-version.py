#!/usr/bin/env python3
"""Keep the private AE Tuner vehicle-test candidate version synchronized."""
import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = (
    ("src/main/java/se/anders/tunerstudio/aetuner/host/BuildIdentity.java",
     re.compile(r'(?m)^    public static final String VERSION = "([^"]+)";$')),
    ("pom.xml", re.compile(r'(?m)^    <version>([^<]+)</version>$')),
    ("src/test/java/se/anders/tunerstudio/aetuner/VehicleTestIdentityRegressionTest.java",
     re.compile(r'(?m)^    private static final String EXPECTED_VERSION = "([^"]+)";$')),
)
VEHICLE_TEST = re.compile(r'^(\d+\.\d+\.\d+)-vehicle-test\.(\d+)$')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("next_version", nargs="?")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check == bool(args.next_version):
        parser.error("provide either --check or a next vehicle-test version")
    records = []
    for relative, pattern in SOURCES:
        path = ROOT / relative
        body = path.read_text(encoding="utf-8")
        matches = list(pattern.finditer(body))
        if len(matches) != 1:
            parser.error("{}: expected one version declaration, found {}".format(relative, len(matches)))
        records.append((path, body, matches[0]))
    versions = [match.group(1) for _, _, match in records]
    if len(set(versions)) != 1:
        parser.error("BuildIdentity, Maven and identity regression versions disagree: {}".format(versions))
    previous = versions[0]
    if args.check:
        if not (VEHICLE_TEST.fullmatch(previous) or re.fullmatch(r"\d+\.\d+\.\d+", previous)):
            parser.error("unsupported project version: {}".format(previous))
        print("Version declarations agree: {}".format(previous))
        return
    old = VEHICLE_TEST.fullmatch(previous)
    if not old:
        parser.error("only vehicle-test versions support auto-increment")
    new = VEHICLE_TEST.fullmatch(args.next_version)
    if not new or new.group(1) != old.group(1):
        parser.error("next version must retain the same product version")
    if int(new.group(2)) != int(old.group(2)) + 1:
        parser.error("vehicle-test counter must increment by exactly one")
    changes = []
    for path, body, match in records:
        line = match.group(0).replace(previous, args.next_version, 1)
        changes.append((path, body[:match.start()] + line + body[match.end():]))
    for path, content in changes:
        path.write_text(content, encoding="utf-8")
        print("{}: {} -> {}".format(path.relative_to(ROOT), previous, args.next_version))


if __name__ == "__main__":
    main()
