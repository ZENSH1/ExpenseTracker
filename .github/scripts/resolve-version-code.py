#!/usr/bin/env python3
"""Pick a versionCode that Play will actually accept.

The tag determines the version code (see the "Resolve version" step). That is
reproducible, but it collides whenever the same MAJOR.MINOR.PATCH is published
twice -- v0.1.0-alpha followed by v0.1.0, say, or a second internal build of the
same version. Play rejects a duplicate or lower code, and it does so only after
the whole bundle has been built and uploaded.

So ask Play what it already has. If the tag-derived code is free, use it. If it
is taken, step just past the highest code in use and say so in the log.

Failure here is never fatal: if Play cannot be reached the tag-derived code is
kept and the upload step reports the collision as it would have anyway.

Reads   PLAY_SERVICE_ACCOUNT_JSON, PACKAGE_NAME, VERSION_CODE
Writes  VERSION_CODE to $GITHUB_ENV (only when it needs to change)
"""

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

TOKEN_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def warn(msg):
    print(f"::warning::{msg}")


def b64url(raw: bytes) -> bytes:
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def access_token(sa: dict) -> str:
    """Exchange the service account key for an OAuth token (RS256 via openssl)."""
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64url(
        json.dumps(
            {
                "iss": sa["client_email"],
                "scope": TOKEN_SCOPE,
                "aud": sa["token_uri"],
                "iat": now,
                "exp": now + 600,
            }
        ).encode()
    )
    signing_input = header + b"." + claims

    with tempfile.TemporaryDirectory() as tmp:
        key_path = os.path.join(tmp, "key.pem")
        msg_path = os.path.join(tmp, "msg.bin")
        with open(key_path, "w", newline="") as fh:
            fh.write(sa["private_key"])
        with open(msg_path, "wb") as fh:
            fh.write(signing_input)
        proc = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_path, msg_path],
            capture_output=True,
        )
    if proc.returncode != 0:
        raise RuntimeError(f"openssl signing failed: {proc.stderr.decode()[:200]}")

    jwt = signing_input + b"." + b64url(proc.stdout)
    body = urllib.parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": jwt.decode(),
        }
    ).encode()
    with urllib.request.urlopen(
        urllib.request.Request(sa["token_uri"], data=body), timeout=30
    ) as resp:
        return json.load(resp)["access_token"]


def call(method: str, url: str, token: str):
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read()
    return json.loads(raw) if raw else {}


def highest_version_code(package: str, token: str) -> int:
    """Highest versionCode Play already knows about, across every track.

    An edit is a scratch transaction. Listing bundles needs one, so create it,
    read, and always delete it again -- an edit that is never committed changes
    nothing, and deleting it avoids leaving drafts lying around.
    """
    edit_id = call("POST", f"{API}/{package}/edits", token)["id"]
    try:
        codes = [
            int(b["versionCode"])
            for b in call(
                "GET", f"{API}/{package}/edits/{edit_id}/bundles", token
            ).get("bundle", [])
        ]
        # APKs share the versionCode namespace with bundles.
        codes += [
            int(a["versionCode"])
            for a in call(
                "GET", f"{API}/{package}/edits/{edit_id}/apks", token
            ).get("apks", [])
        ]
    finally:
        try:
            call("DELETE", f"{API}/{package}/edits/{edit_id}", token)
        except Exception as exc:  # noqa: BLE001 - cleanup must not mask the result
            warn(f"Could not delete the scratch Play edit: {exc}")
    return max(codes, default=0)


def main() -> int:
    try:
        wanted = int(os.environ["VERSION_CODE"])
    except (KeyError, ValueError):
        warn("VERSION_CODE is unset or not a number; leaving it alone.")
        return 0

    package = os.environ.get("PACKAGE_NAME", "")
    raw_sa = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw_sa or not package:
        warn("No Play credentials available; keeping the tag-derived versionCode.")
        return 0

    try:
        sa = json.loads(raw_sa)
        token = access_token(sa)
        highest = highest_version_code(package, token)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:300]
        warn(
            f"Could not read existing versionCodes from Play (HTTP {exc.code}): {detail} "
            f"Keeping the tag-derived versionCode {wanted}."
        )
        return 0
    except Exception as exc:  # noqa: BLE001 - never block a release on this check
        warn(
            f"Could not read existing versionCodes from Play ({type(exc).__name__}: {exc}). "
            f"Keeping the tag-derived versionCode {wanted}."
        )
        return 0

    if highest == 0:
        print(f"Play has no bundles yet. Using versionCode {wanted}.")
        return 0

    if wanted > highest:
        print(f"Highest versionCode on Play is {highest}; {wanted} is free.")
        return 0

    bumped = highest + 1
    warn(
        f"versionCode {wanted} is already used on Play (highest in use: {highest}). "
        f"Uploading as {bumped} instead. The version name is unchanged, so consider "
        f"bumping the patch number on the next tag to keep tags and codes aligned."
    )
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a", encoding="utf-8") as fh:
            fh.write(f"VERSION_CODE={bumped}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
