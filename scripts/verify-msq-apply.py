#!/usr/bin/env python3
"""Verify that an AE Tuner apply changed only explicitly declared MSQ values.

Usage:
  python3 scripts/verify-msq-apply.py before.msq after.msq expected-write-plan.json

Expected manifest example:
{
  "changes": [
    {
      "parameter": "predictiveMapBlendDurationValues",
      "index": 1,
      "before": 0.26,
      "after": 0.54
    }
  ]
}

For a scalar constant omit "index". The comparison is semantic: TunerStudio
bibliography/write timestamps and XML formatting are ignored; every <constant>
value is compared by parameter name and flattened value index.
"""

from __future__ import print_function

import argparse
import json
import math
import os
import sys
import tempfile
import xml.etree.ElementTree as ET

EPSILON = 1.0e-6


def local_name(tag):
    return tag.split("}", 1)[-1] if "}" in tag else tag


def parse_token(token):
    value = token.strip()
    if not value:
        return None
    if value.startswith('"') and value.endswith('"'):
        return value
    try:
        return float(value)
    except ValueError:
        return value


def read_constants(path):
    root = ET.parse(path).getroot()
    result = {}
    for element in root.iter():
        if local_name(element.tag) != "constant":
            continue
        name = element.attrib.get("name")
        if not name:
            continue
        if name in result:
            raise ValueError("duplicate <constant> name in MSQ: %s" % name)
        text = "".join(element.itertext()).strip()
        tokens = [parse_token(token) for token in text.split()]
        tokens = [token for token in tokens if token is not None]
        # Quoted scalar strings can contain spaces. Keep the complete text as
        # one semantic value rather than tokenizing it into unrelated words.
        if text.startswith('"') and text.endswith('"'):
            tokens = [text]
        result[name] = tokens
    return result


def numeric_equal(a, b):
    if isinstance(a, float) and isinstance(b, float):
        return math.isfinite(a) and math.isfinite(b) and abs(a - b) <= EPSILON
    return a == b


def flatten_changes(before, after):
    differences = []
    all_names = sorted(set(before) | set(after))
    for name in all_names:
        if name not in before:
            differences.append((name, None, "<missing>", after[name]))
            continue
        if name not in after:
            differences.append((name, None, before[name], "<missing>"))
            continue
        left = before[name]
        right = after[name]
        if len(left) != len(right):
            differences.append((name, None,
                                "length=%d" % len(left),
                                "length=%d" % len(right)))
            continue
        for index, (old, new) in enumerate(zip(left, right)):
            if not numeric_equal(old, new):
                differences.append((name, index, old, new))
    return differences


def normalize_expected(manifest):
    changes = manifest.get("changes")
    if not isinstance(changes, list) or not changes:
        raise ValueError("expected manifest must contain a non-empty changes list")
    result = {}
    for item in changes:
        if not isinstance(item, dict):
            raise ValueError("each expected change must be an object")
        parameter = item.get("parameter")
        if not parameter:
            raise ValueError("expected change is missing parameter")
        index = item.get("index")
        if index is not None:
            index = int(index)
            if index < 0:
                raise ValueError("expected array index cannot be negative")
        key = (parameter, index)
        if key in result:
            raise ValueError("duplicate expected target %s" % (key,))
        result[key] = (float(item["before"]), float(item["after"]))
    return result


def value_at(constants, parameter, index):
    if parameter not in constants:
        raise ValueError("MSQ is missing expected parameter %s" % parameter)
    values = constants[parameter]
    if index is None:
        if len(values) != 1:
            raise ValueError("expected scalar %s has %d values" %
                             (parameter, len(values)))
        return values[0]
    if index >= len(values):
        raise ValueError("expected target %s[%d] outside %d value(s)" %
                         (parameter, index, len(values)))
    return values[index]


def verify(before_path, after_path, manifest_path):
    before = read_constants(before_path)
    after = read_constants(after_path)
    with open(manifest_path, "r") as handle:
        expected = normalize_expected(json.load(handle))

    errors = []
    for (parameter, index), (old_expected, new_expected) in expected.items():
        try:
            old_actual = value_at(before, parameter, index)
            new_actual = value_at(after, parameter, index)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if not isinstance(old_actual, float) or not numeric_equal(old_actual, old_expected):
            errors.append("baseline mismatch %s%s: expected %s, before MSQ has %s" %
                          (parameter, "" if index is None else "[%d]" % index,
                           old_expected, old_actual))
        if not isinstance(new_actual, float) or not numeric_equal(new_actual, new_expected):
            errors.append("applied mismatch %s%s: expected %s, after MSQ has %s" %
                          (parameter, "" if index is None else "[%d]" % index,
                           new_expected, new_actual))

    actual_differences = flatten_changes(before, after)
    expected_keys = set()
    for parameter, index in expected:
        # MSQ arrays and scalars both become flattened token lists. Scalars use
        # index 0 in the actual-difference representation.
        expected_keys.add((parameter, 0 if index is None else index))

    unexpected = []
    observed_expected = set()
    for parameter, index, old, new in actual_differences:
        key = (parameter, index)
        if key in expected_keys:
            observed_expected.add(key)
        else:
            unexpected.append((parameter, index, old, new))

    missing_changes = expected_keys - observed_expected
    for parameter, index in sorted(missing_changes):
        errors.append("declared target did not change: %s[%d]" % (parameter, index))

    print("AE Tuner semantic MSQ apply verification")
    print("Before: %s" % before_path)
    print("After:  %s" % after_path)
    print("Constants checked: %d" % len(set(before) | set(after)))
    print("Expected changed values: %d" % len(expected_keys))
    print("Observed changed values: %d" % len(actual_differences))

    if unexpected:
        print("UNEXPECTED CHANGES:")
        for parameter, index, old, new in unexpected:
            suffix = "" if index is None else "[%d]" % index
            print("  %s%s: %s -> %s" % (parameter, suffix, old, new))
        errors.append("%d unexpected MSQ value change(s)" % len(unexpected))
    else:
        print("UNEXPECTED CHANGES: NONE")

    if errors:
        print("RESULT: FAIL")
        for error in errors:
            print("  - %s" % error)
        return 1

    print("RESULT: PASS")
    return 0


def self_test():
    before_xml = '''<?xml version="1.0"?>
<msq xmlns="http://www.msefi.com/:msq"><page>
<constant cols="1" name="predictiveMapBlendDurationValues" rows="4">0.18 0.26 0.22 0.18</constant>
<constant name="unchanged">7.0</constant>
</page></msq>'''
    after_xml = '''<?xml version="1.0"?>
<msq xmlns="http://www.msefi.com/:msq"><page>
<constant cols="1" name="predictiveMapBlendDurationValues" rows="4">0.18 0.54 0.22 0.18</constant>
<constant name="unchanged">7.0</constant>
</page></msq>'''
    bad_xml = after_xml.replace('<constant name="unchanged">7.0</constant>',
                                '<constant name="unchanged">8.0</constant>')
    manifest = {
        "changes": [{
            "parameter": "predictiveMapBlendDurationValues",
            "index": 1,
            "before": 0.26,
            "after": 0.54
        }]
    }
    directory = tempfile.mkdtemp(prefix="ae-tuner-msq-verify-")
    before_path = os.path.join(directory, "before.msq")
    after_path = os.path.join(directory, "after.msq")
    bad_path = os.path.join(directory, "bad.msq")
    manifest_path = os.path.join(directory, "plan.json")
    for path, content in ((before_path, before_xml),
                          (after_path, after_xml),
                          (bad_path, bad_xml)):
        with open(path, "w") as handle:
            handle.write(content)
    with open(manifest_path, "w") as handle:
        json.dump(manifest, handle)
    if verify(before_path, after_path, manifest_path) != 0:
        raise AssertionError("self-test expected valid MSQ pair to pass")
    if verify(before_path, bad_path, manifest_path) == 0:
        raise AssertionError("self-test expected unrelated MSQ change to fail")
    print("verify-msq-apply.py self-test passed")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("before", nargs="?")
    parser.add_argument("after", nargs="?")
    parser.add_argument("manifest", nargs="?")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.before or not args.after or not args.manifest:
        parser.error("before.msq after.msq and expected-write-plan.json are required")
    return verify(args.before, args.after, args.manifest)


if __name__ == "__main__":
    sys.exit(main())
